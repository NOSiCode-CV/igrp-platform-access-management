package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-11 (#19), #33: kill ends a user's access on the whole platform (every
 * authorization revoked, every permission key deleted); refresh recomputes one
 * application's key from current roles, accepting nothing from the caller.
 * There are no sessions: a user is signed in while they hold an active
 * authorization.
 */
class AccessKillAndRefreshIT extends AbstractCompatIT {

    private static final String FETCH = "/api/users/me/permissions?applicationCode=";
    private static final String KILL_BODY = "{\"reason\":\"compat\",\"killedBy\":\"compat\"}";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    OAuth2AuthorizationService authorizationService;
    @Autowired
    RegisteredClientRepository registeredClients;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String app;
    private String otherApp;
    private String userId;
    private String email;
    private String token;
    private RegisteredClient client;

    @BeforeEach
    void userSignedInTwiceWithKeysInTwoApplications() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "ks-" + suffix.toLowerCase();
        app = "KS_APP_" + suffix;
        otherApp = "KS_OTHER_" + suffix;
        int appId = api.createApplication(app);
        api.createApplication(otherApp);
        CompatSupport.M2MClient m2m = api.registerM2MClient(service + "-client", appId);
        client = registeredClients.findByClientId(m2m.clientId());
        ok(api.postJson("/api/routes/register", api.serviceToken(m2m),
                "{\"service\":" + CompatSupport.q(service) + ",\"version\":\"1.0.0\",\"routes\":["
                        + route("GET", "/api/v1/faturas", key("list")) + ","
                        + route("POST", "/api/v1/faturas", key("create")) + "]}"));

        userId = UUID.randomUUID().toString();
        email = "ks-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Kill User', 'ACTIVE')""", userId, email, email);
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept())
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept() + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        ok(api.postJson("/api/departments/" + dept() + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "," + CompatSupport.q(key("create")) + "]"));
        ok(api.postJson("/api/departments/" + dept() + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role()) + ",\"name\":\"Role\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept() + "/roles/" + role() + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "]"));
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept() + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role()) + "]}"));

        token = api.userToken(userId, email);
        assertThat(fetch(token, app).statusCode()).isEqualTo(200);
        assertThat(fetch(token, otherApp).statusCode()).isEqualTo(200);
        assertThat(redis.hasKey(setKey(app))).isTrue();
        assertThat(redis.hasKey(setKey(otherApp))).isTrue();
        // The token's sign-in (CompatSupport.userToken), and one from another device.
        authorization(userId);
        // A federated sign-in stores the upstream IdP subject as principal name.
        String upstreamSubject = "upstream-" + suffix.toLowerCase();
        jdbc.update("""
                INSERT INTO t_user_identity (id, provider, user_id, igrp_user_id) VALUES (?, 'external-idp', ?, ?)""",
                UUID.randomUUID(), upstreamSubject, userId);
        authorization(upstreamSubject);
        assertThat(authorizations()).isEqualTo(3);
    }

    @AfterEach
    void retireDepartment() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", dept());
    }

    @Test
    @DisplayName("Killing a user ends every sign-in: every key and authorization gone; the next miss is 401")
    void killUser() {
        String secondToken = api.userToken(userId, email);

        ok(api.postJson("/api/admin/sessions/users/" + userId + "/kill", admin, KILL_BODY));

        assertKilled();
        assertThat(fetch(secondToken, app).statusCode()).as("the other sign-in ends too").isEqualTo(401);
    }

    @Test
    @DisplayName("Killing an unknown user answers 404")
    void killUnknownUser() {
        assertThat(api.postJson("/api/admin/sessions/users/" + UUID.randomUUID() + "/kill", admin, KILL_BODY)
                .statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("The session routes are gone (#33)")
    void sessionRoutesAreGone() {
        assertThat(api.postJson("/api/admin/users/" + userId + "/logout-all", admin, KILL_BODY).statusCode())
                .isEqualTo(404);
        assertThat(api.postJson("/api/admin/users/" + userId + "/force-reauth", admin, KILL_BODY).statusCode())
                .isEqualTo(404);
        assertThat(api.get("/api/admin/sessions", admin).statusCode()).isIn(404, 405);
        assertThat(api.get("/api/session/check", token).statusCode()).isIn(403, 404);
        assertThat(redis.hasKey(setKey(app))).as("nothing was killed").isTrue();
    }

    @Test
    @DisplayName("Killing by role kills every user holding it")
    void killByRole() {
        ok(api.postJson("/api/admin/sessions/roles/" + role() + "/kill?departmentCode=" + dept(), admin,
                KILL_BODY));

        assertKilled();
    }

    @Test
    @DisplayName("Killing by department kills every user in it")
    void killByDepartment() {
        ok(api.postJson("/api/admin/sessions/departments/" + dept() + "/kill", admin, KILL_BODY));

        assertKilled();
    }

    @Test
    @DisplayName("Refresh writes exactly what the permission fetch computes, from current roles")
    void refreshRecomputes() {
        // A grant made behind AM's back: no event, so the set is stale.
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission)
                SELECT r.id, p.id FROM t_role r, t_permission p WHERE r.code = ? AND p.permission_key = ?""",
                role(), key("create"));
        assertThat(redis.opsForSet().members(setKey(app))).doesNotContain(key("create"));

        HttpResponse<String> refreshed = refresh(userId, app, "");

        assertThat(refreshed.statusCode()).as(refreshed.body()).isEqualTo(200);
        Map<String, Object> body = CompatSupport.jsonObject(refreshed.body());
        assertThat(keys(body)).containsExactlyInAnyOrder(key("list"), key("create"));
        assertThat(redis.opsForSet().members(setKey(app)))
                .containsExactlyInAnyOrder(key("list"), key("create"), "~");
        redis.delete(setKey(app));
        Map<String, Object> fetched = CompatSupport.jsonObject(fetch(token, app).body());
        assertThat(body).as("the same as the user's own fetch").isEqualTo(fetched);
    }

    @Test
    @DisplayName("Refresh does not give a killed user their key back")
    void refreshAfterKillWritesNothing() {
        ok(api.postJson("/api/admin/sessions/users/" + userId + "/kill", admin, KILL_BODY));

        assertThat(refresh(userId, app, "").statusCode()).isEqualTo(409);
        assertThat(redis.hasKey(setKey(app))).isFalse();
    }

    @Test
    @DisplayName("The kill and the admin who asked are in the audit trail")
    void killedByIsRecorded() {
        ok(api.postJson("/api/admin/sessions/users/" + userId + "/kill", admin, KILL_BODY));

        assertThat(jdbc.queryForList("""
                SELECT context_data FROM t_security_audit_log WHERE user_id = ? AND event_type = 'TOKEN_REVOKED'""",
                String.class, userId)).singleElement().asString().contains("\"actor\":\"compat\"");
    }

    @Test
    @DisplayName("Refresh accepts nothing written by hand: a body is ignored")
    void refreshIgnoresAnyBody() {
        HttpResponse<String> refreshed = refresh(userId, app,
                "{\"keys\":[\"evil.everything.all\"],\"scopes\":{\"evil.everything.all\":\"all\"}}");

        assertThat(refreshed.statusCode()).as(refreshed.body()).isEqualTo(200);
        assertThat(redis.opsForSet().members(setKey(app))).containsExactlyInAnyOrder(key("list"), "~");
    }

    @Test
    @DisplayName("Refresh refuses an inactive user and an unknown application, and writes nothing")
    void refreshRefusals() {
        redis.delete(setKey(app));
        jdbc.update("UPDATE t_user SET status = 'INACTIVE' WHERE id = ?", userId);

        assertThat(refresh(userId, app, "").statusCode()).isEqualTo(409);
        assertThat(redis.hasKey(setKey(app))).isFalse();
        assertThat(refresh(UUID.randomUUID().toString(), app, "").statusCode()).isEqualTo(404);
        jdbc.update("UPDATE t_user SET status = 'ACTIVE' WHERE id = ?", userId);
        assertThat(refresh(userId, "KS_NO_SUCH_" + suffix, "").statusCode()).isEqualTo(404);
    }

    // ---- fixtures ----

    private void assertKilled() {
        assertThat(redis.hasKey(setKey(app))).as("key of the first application").isFalse();
        assertThat(redis.hasKey(setKey(otherApp))).as("key of the second application").isFalse();
        assertThat(redis.hasKey("permissions:user:" + userId + ":apps")).as("the index").isFalse();
        assertThat(authorizations()).as("every authorization revoked").isZero();
        assertThat(fetch(token, app).statusCode()).as("the next miss").isEqualTo(401);
    }

    private void authorization(String principalName) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-" + UUID.randomUUID(), now, now.plusSeconds(300), Set.of("openid"));
        authorizationService.save(OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(principalName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .token(access, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", userId)))
                .refreshToken(new OAuth2RefreshToken("refresh-" + UUID.randomUUID(), now, now.plusSeconds(3600)))
                .build());
    }

    private long authorizations() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?
                    OR principal_name IN (SELECT user_id FROM t_user_identity WHERE igrp_user_id = ?)""",
                Long.class, userId, userId);
    }

    private HttpResponse<String> refresh(String user, String applicationCode, String body) {
        return api.postJson("/api/admin/sessions/users/" + user + "/refresh?applicationCode=" + applicationCode,
                admin, body);
    }

    private HttpResponse<String> fetch(String bearer, String applicationCode) {
        return api.get(FETCH + applicationCode, bearer);
    }

    @SuppressWarnings("unchecked")
    private static List<String> keys(Map<String, Object> body) {
        return (List<String>) body.get("keys");
    }

    private String key(String action) {
        return service + ".faturas." + action;
    }

    private String dept() {
        return "KS_D_" + suffix;
    }

    private String role() {
        return "KS_R_" + suffix;
    }

    private String setKey(String applicationCode) {
        return "permissions:user:" + userId + ":app:" + applicationCode;
    }

    private static String route(String method, String path, String key) {
        return "{\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"key\":\"" + key + "\",\"annotated\":false}";
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
