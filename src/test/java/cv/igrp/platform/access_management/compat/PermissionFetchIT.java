package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-06 (#14): AM computes what a caller may do in one application and writes
 * it to the shared Redis, where every service reads it.
 * {@code permissions:user:{sub}:app:{appCode}} is a Set of keys plus the "~"
 * marker, with a companion {@code :scopes} hash, both with a TTL; a per-user
 * index lists the user's application keys.
 */
class PermissionFetchIT extends AbstractCompatIT {

    private static final String FETCH = "/api/users/me/permissions?applicationCode=";
    private static final String MARKER = "~";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String app;
    private String userId;
    private String email;

    @BeforeEach
    void applicationWithRegisteredKeys() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "pf-" + suffix.toLowerCase();
        app = "PF_APP_" + suffix;
        int appId = api.createApplication(app);
        String serviceToken = api.serviceToken(api.registerM2MClient(service + "-client", appId));
        HttpResponse<String> registered = api.postJson("/api/routes/register", serviceToken,
                "{\"service\":" + CompatSupport.q(service) + ",\"version\":\"1.0.0\",\"routes\":["
                        + route("GET", "/api/v1/faturas", key("faturas.list")) + ","
                        + route("POST", "/api/v1/faturas", key("faturas.create")) + ","
                        + route("DELETE", "/api/v1/faturas/{id}", key("faturas.delete")) + "]}");
        assertThat(registered.statusCode()).as(registered.body()).isEqualTo(200);

        userId = UUID.randomUUID().toString();
        email = "pf-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Permission Fetch User', 'ACTIVE')""", userId, email, email);
    }

    @AfterEach
    void retireDepartments() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "PF\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("The fetch returns the active role's keys and writes the set, the scopes and the index, with a TTL")
    void fetchWritesTheSet() {
        department("D1", "R1");
        grant("R1", "faturas.list", "faturas.create");
        assign("D1", "R1");

        Map<String, Object> body = fetch(userToken());

        assertThat(keys(body)).containsExactlyInAnyOrder(key("faturas.list"), key("faturas.create"));
        assertThat(body.get("scopes")).isEqualTo(Map.of(key("faturas.list"), "all", key("faturas.create"), "all"));
        assertThat(redis.opsForSet().members(setKey(userId)))
                .containsExactlyInAnyOrder(key("faturas.list"), key("faturas.create"), MARKER);
        assertThat(redis.<String, String>opsForHash().entries(setKey(userId) + ":scopes"))
                .containsEntry(key("faturas.list"), "all").containsEntry(key("faturas.create"), "all");
        assertThat(redis.getExpire(setKey(userId))).isBetween(2600L, 2700L);
        assertThat(redis.getExpire(setKey(userId) + ":scopes")).isBetween(2600L, 2700L);
        assertThat(redis.opsForSet().members(indexKey(userId))).contains(setKey(userId));
        assertThat(redis.getExpire(indexKey(userId))).isPositive();
    }

    @Test
    @DisplayName("Only the active role counts; no active role is an empty marker; a switch evicts the key after commit")
    void onlyTheActiveRoleCountsAndASwitchEvicts() {
        department("D1", "R1", "R2");
        grant("R1", "faturas.list");
        grant("R2", "faturas.delete");
        assign("D1", "R1", "R2");
        String token = userToken();

        Map<String, Object> noChoice = fetch(token);
        assertThat(keys(noChoice)).isEmpty();
        assertThat(redis.opsForSet().members(setKey(userId))).containsExactly(MARKER);
        assertThat(redis.getExpire(setKey(userId))).as("the marker-only set has a TTL too").isBetween(2600L, 2700L);

        HttpResponse<String> switched = api.putJson("/api/users/me/role/active?applicationCode=" + app, token,
                "{\"roleCode\":" + CompatSupport.q(role("R2")) + "}");
        assertThat(switched.statusCode()).as(switched.body()).isEqualTo(200);
        assertThat(redis.hasKey(setKey(userId))).as("evicted after the switch committed").isFalse();
        assertThat(redis.hasKey(setKey(userId) + ":scopes")).isFalse();

        assertThat(keys(fetch(token))).containsExactly(key("faturas.delete"));
        assertThat(accessedRows()).as("a refill after the switch is not a second access in the TTL").isOne();
    }

    @Test
    @DisplayName("The per-user index lists every application key of the user")
    void indexListsEveryApplication() {
        String other = "PF_OTHER_" + suffix;
        api.createApplication(other);
        department("D1", "R1");
        assign("D1", "R1");
        String token = userToken();

        fetch(token);
        assertThat(api.get(FETCH + other, token).statusCode()).isEqualTo(200);

        assertThat(redis.opsForSet().members(indexKey(userId)))
                .contains(setKey(userId), "permissions:user:" + userId + ":app:" + other);
    }

    @Test
    @DisplayName("APPLICATION_ACCESSED is audited once per user, application and TTL")
    void applicationAccessedOncePerTtl() {
        department("D1", "R1");
        assign("D1", "R1");
        String token = userToken();

        fetch(token);
        // No permissions: the set still exists, holding only the marker, so a reader has a hit.
        assertThat(redis.hasKey(setKey(userId))).isTrue();
        assertThat(redis.opsForSet().members(setKey(userId))).containsExactly(MARKER);
        fetch(token);

        assertThat(accessedRows()).isOne();
    }

    private long accessedRows() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE event_type = 'APPLICATION_ACCESSED' AND user_id = ? AND context_data LIKE ?""",
                Long.class, userId, "%" + app + "%");
    }

    @Test
    @DisplayName("A caller without an active authorization gets 401 and nothing is written")
    void inactiveCallerGets401() {
        department("D1", "R1");
        assign("D1", "R1");
        String token = userToken();
        jdbc.update("UPDATE t_user SET status = 'INACTIVE' WHERE id = ?", userId);

        HttpResponse<String> response = api.get(FETCH + app, token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertNothingWritten(userId);
    }

    @Test
    @DisplayName("A service account whose client is disabled gets 401 and nothing is written")
    void inactiveServiceAccountClientGets401() {
        CompatSupport.M2MClient client = api.registerM2MClient(service + "-off", null, List.of(key("faturas.list")));
        String token = api.serviceToken(client);
        String serviceAccountId = serviceAccountId(client);
        jdbc.update("UPDATE t_oauth_client SET active = FALSE WHERE client_id = ?", client.clientId());

        HttpResponse<String> response = api.get(FETCH + app, token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertNothingWritten(serviceAccountId);
    }

    private void assertNothingWritten(String sub) {
        assertThat(redis.hasKey(setKey(sub))).isFalse();
        assertThat(redis.hasKey(setKey(sub) + ":scopes")).isFalse();
        assertThat(redis.hasKey(indexKey(sub))).isFalse();
    }

    private String serviceAccountId(CompatSupport.M2MClient client) {
        return jdbc.queryForObject("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                 WHERE oc.client_id = ?""", String.class, client.clientId());
    }

    @Test
    @DisplayName("For a service account, \"me\" is the service account: its roles and direct grants")
    void serviceAccountGetsItsGrants() {
        int otherApp = api.createApplication("PF_SA_" + suffix);
        CompatSupport.M2MClient client = api.registerM2MClient(service + "-caller", otherApp,
                List.of(key("faturas.delete")));
        String serviceAccountId = serviceAccountId(client);

        Map<String, Object> body = fetch(api.serviceToken(client));

        assertThat(keys(body)).containsExactly(key("faturas.delete"));
        assertThat(redis.opsForSet().members(setKey(serviceAccountId))).contains(key("faturas.delete"), MARKER);
        assertThat(redis.getExpire(setKey(serviceAccountId))).isBetween(2600L, 2700L);
    }

    @Test
    @DisplayName("Root gets every active key of the application")
    void rootGetsEveryKey() {
        assertThat(keys(fetch(admin))).containsExactlyInAnyOrder(
                key("faturas.list"), key("faturas.create"), key("faturas.delete"));
    }

    @Test
    @DisplayName("An unknown application is 404")
    void unknownApplication() {
        assertThat(api.get(FETCH + "NO_SUCH_" + suffix, userToken()).statusCode()).isEqualTo(404);
    }

    // ---- fixtures ----

    private String key(String suffixKey) {
        return service + "." + suffixKey;
    }

    private String role(String name) {
        return "PF_" + name + "_" + suffix;
    }

    private String dept(String name) {
        return "PF_" + name + "_" + suffix;
    }

    private String setKey(String sub) {
        return "permissions:user:" + sub + ":app:" + app;
    }

    private static String indexKey(String sub) {
        return "permissions:user:" + sub + ":apps";
    }

    private static String route(String method, String path, String key) {
        return "{\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"key\":\"" + key + "\",\"annotated\":false}";
    }

    private void department(String name, String... roles) {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        for (String r : roles) {
            ok(api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}"));
        }
    }

    private void grant(String roleName, String... keys) {
        for (String k : keys) {
            jdbc.update("""
                    INSERT INTO t_role_permission (role_id, permission)
                    SELECT r.id, p.id FROM t_role r, t_permission p WHERE r.code = ? AND p.permission_key = ?""",
                    role(roleName), key(k));
        }
    }

    private void assign(String department, String... roles) {
        StringBuilder codes = new StringBuilder();
        for (String r : roles) {
            codes.append(codes.isEmpty() ? "" : ",").append(CompatSupport.q(role(r)));
        }
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", admin,
                "{\"roles\":[" + codes + "]}"));
    }

    private String userToken() {
        return api.userToken(userId, email);
    }

    private Map<String, Object> fetch(String bearer) {
        HttpResponse<String> response = api.get(FETCH + app, bearer);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    @SuppressWarnings("unchecked")
    private static List<String> keys(Map<String, Object> body) {
        return (List<String>) body.get("keys");
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
