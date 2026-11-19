package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cv.igrp.platform.access_management.compat.CompatSupport.jsonObject;
import static cv.igrp.platform.access_management.compat.CompatSupport.q;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v3-20 (#28; spec "Token exchange and agents"): the RFC 8693 exchange end to end through the
 * real token endpoint, Spring's provider, AM's policy and customizer, the authorization store in
 * Postgres and the security audit. The user's token is stored the way a sign-in leaves it, with
 * its principal and claims, because the exchange reads both back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenExchangeIT extends AbstractCompatIT {

    private static final String EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

    @Autowired
    ApplicationContext context;

    CompatSupport api;
    JdbcTemplate jdbc;
    String callerSecret;
    String userId;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        jdbc = context.getBean(JdbcTemplate.class);
        userId = api.superadminId();
        int application = api.createApplication("te-target-app");
        api.registerM2MClient("te-target", application);
        callerSecret = createExchangeClient("te-caller");
    }

    @Test
    @DisplayName("The exchanged token is thin: the user, the one target, the caller as act, and no longer-lived")
    void exchange() {
        String subject = signedIn(userId, Instant.now().plusSeconds(240));

        HttpResponse<String> response = exchange(subject, "te-target", null);

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> claims = payload((String) jsonObject(response.body()).get("access_token"));
        assertEquals(userId, claims.get("sub"));
        // A single audience is written as a string (RFC 7519 allows either form).
        Object audience = claims.get("aud");
        assertTrue("te-target".equals(audience) || List.of("te-target").equals(audience), String.valueOf(audience));
        assertEquals("te-caller", claims.get("azp"));
        assertEquals("te-caller", ((Map<?, ?>) claims.get("act")).get("sub"));
        assertTrue(((Number) claims.get("exp")).longValue() <= Instant.now().plusSeconds(241).getEpochSecond());
        for (String fat : List.of("permissions", "resource_access", "selectedRole", "org", "sid")) {
            assertFalse(claims.containsKey(fat), fat + " in " + claims);
        }
        assertNull(jsonObject(response.body()).get("refresh_token"));
    }

    @Test
    @DisplayName("With the caller's own client_credentials token as actor, act names its service account; "
            + "the issuance is audited with agent and act")
    void exchangeWithActor() {
        String subject = signedIn(userId, Instant.now().plusSeconds(240));
        String actor = (String) jsonObject(api.postForm("/oauth2/token", "te-caller", callerSecret,
                "grant_type=client_credentials").body()).get("access_token");
        String account = jdbc.queryForObject("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client c ON c.id = sa.oauth_client_id
                 WHERE c.client_id = 'te-caller'""", String.class);

        HttpResponse<String> response = exchange(subject, "te-target", actor);

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> claims = payload((String) jsonObject(response.body()).get("access_token"));
        assertEquals(account, ((Map<?, ?>) claims.get("act")).get("sub"));
        Long audited = jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE event_type = 'TOKEN_ISSUED' AND agent = ? AND act LIKE ?""",
                Long.class, account, "%" + account + "%");
        assertEquals(1L, audited);
    }

    @Test
    @DisplayName("An audience that is not an active client is refused with invalid_target")
    void unknownAudience() {
        HttpResponse<String> response = exchange(signedIn(userId, Instant.now().plusSeconds(240)), "nobody", null);

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("invalid_target", jsonObject(response.body()).get("error"));
    }

    @Test
    @DisplayName("After a logout or kill the user's token is gone from the store: the exchange is refused")
    void signedOutUser() {
        String subject = signedIn(userId, Instant.now().plusSeconds(240));
        jdbc.update("DELETE FROM oauth2_authorization WHERE access_token_value = ?", subject);

        HttpResponse<String> response = exchange(subject, "te-target", null);

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("invalid_grant", jsonObject(response.body()).get("error"));
    }

    @Test
    @DisplayName("A client without the token-exchange grant cannot exchange")
    void clientWithoutTheGrant() {
        CompatSupport.M2MClient plain = api.registerM2MClient("te-plain");
        HttpResponse<String> response = api.postForm("/oauth2/token", plain.clientId(), plain.clientSecret(),
                form(signedIn(userId, Instant.now().plusSeconds(240)), "te-target", null));

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("unauthorized_client", jsonObject(response.body()).get("error"));
    }

    private HttpResponse<String> exchange(String subject, String audience, String actor) {
        return api.postForm("/oauth2/token", "te-caller", callerSecret, form(subject, audience, actor));
    }

    private static String form(String subject, String audience, String actor) {
        String form = "grant_type=" + enc(EXCHANGE) + "&subject_token=" + enc(subject)
                + "&subject_token_type=" + enc(ACCESS_TOKEN) + "&requested_token_type=" + enc(ACCESS_TOKEN)
                + "&audience=" + enc(audience);
        return actor == null ? form : form + "&actor_token=" + enc(actor) + "&actor_token_type=" + enc(ACCESS_TOKEN);
    }

    /** A service client allowed both client_credentials and the token exchange, with a service account. */
    private String createExchangeClient(String clientId) {
        String admin = api.superadminToken();
        HttpResponse<String> created = api.postJson("/api/clients", admin,
                "{\"clientId\":" + q(clientId) + ",\"clientName\":" + q(clientId) + ",\"scopes\":[\"igrp.m2m\"],"
                        + "\"grantTypes\":[\"client_credentials\"," + q(EXCHANGE) + "]}");
        assertEquals(2, created.statusCode() / 100, created.body());
        Map<String, Object> client = jsonObject(created.body());
        HttpResponse<String> account = api.postJson("/api/service-accounts", admin,
                "{\"name\":" + q(clientId + " SA") + ",\"oauthClientId\":" + q((String) client.get("id"))
                        + ",\"permissionIds\":[]}");
        assertEquals(2, account.statusCode() / 100, account.body());
        return (String) client.get("clientSecret");
    }

    /** A user's access token stored as a sign-in stores it: with its principal and its claims. */
    private String signedIn(String user, Instant expiresAt) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(CompatEnvironment.ISSUER).subject(user)
                .audience(List.of("igrp-access-management")).issuedAt(now).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).build();
        String token = context.getBean(JwtEncoder.class)
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        RegisteredClient client = context.getBean(RegisteredClientRepository.class).findByClientId("te-caller");
        assertNotNull(client);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(user).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(Principal.class.getName(), UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token, now, expiresAt),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()))
                .build();
        context.getBean(OAuth2AuthorizationService.class).save(authorization);
        return token;
    }

    private static Map<String, Object> payload(String jwt) {
        String json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return jsonObject(json);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
