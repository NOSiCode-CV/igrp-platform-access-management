package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static cv.igrp.platform.access_management.compat.CompatSupport.jsonObject;
import static cv.igrp.platform.access_management.compat.CompatSupport.q;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 migration gate — a real browser login, as the Application
 * Center performs it: an OAuth2 client of this service runs the
 * {@code authorization_code} flow with PKCE, this service federates the user
 * to the external IdP (Keycloak), provisions them, and issues its own tokens.
 * Then refresh (with reuse detection) and logout.
 *
 * <p>Every persisted security format is on this path: the pending OAuth2
 * request and saved request in the shared servlet session
 * ({@code SPRING_SESSION_ATTRIBUTES}), the federated {@code IgrpOidcUser} in
 * the security context, and the {@code oauth2_authorization} row behind every
 * token. The Jackson 3 stores must carry it unchanged.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OidcLoginFlowIT extends AbstractCompatIT {

    static final String CLIENT_ID = "compat-web";
    static final String REDIRECT_URI = "https://app.example.cv/callback";
    static final String POST_LOGOUT_URI = "https://app.example.cv/logged-out";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;

    CompatSupport api;
    String clientSecret;
    String codeVerifier;
    Map<String, Object> tokens;
    Map<String, Object> refreshed;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        HttpResponse<String> created = api.postJson("/api/clients", api.superadminToken(),
                "{\"clientId\":" + q(CLIENT_ID) + ",\"clientName\":\"Compat Web App\",\"requirePkce\":true,"
                        + "\"scopes\":[\"openid\",\"profile\",\"email\"],"
                        + "\"grantTypes\":[\"authorization_code\",\"refresh_token\"],"
                        + "\"redirectUris\":[" + q(REDIRECT_URI) + "],"
                        + "\"postLogoutRedirectUris\":[" + q(POST_LOGOUT_URI) + "]}");
        assertTrue(created.statusCode() / 100 == 2, created.statusCode() + " " + created.body());
        clientSecret = (String) jsonObject(created.body()).get("clientSecret");
    }

    @Test
    @Order(1)
    @DisplayName("authorization_code + PKCE through the external IdP yields this service's tokens")
    void login() throws Exception {
        tokens = signIn();
    }

    /** authorization_code + PKCE through the external IdP; the token response. */
    private Map<String, Object> signIn() throws Exception {
        TestBrowser browser = new TestBrowser();
        codeVerifier = randomUrlSafe(48);
        String state = randomUrlSafe(16);

        String authorize = CompatEnvironment.ISSUER + "/oauth2/authorize?response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8)
                + "&state=" + state
                + "&code_challenge=" + s256(codeVerifier)
                + "&code_challenge_method=S256";

        // To the IdP's login page: /oauth2/authorize -> /oauth2/authorization/external-idp -> Keycloak.
        TestBrowser.Hop toLogin = browser.followUntil(browser.get(authorize), url -> false);
        assertEquals(200, toLogin.response().statusCode(), "expected the IdP login page; trail: " + toLogin.trail());
        assertTrue(toLogin.response().uri().toString().startsWith(CompatEnvironment.keycloakIssuer()),
                toLogin.response().uri().toString());

        // The pending OAuth2 request must be in the shared JDBC session store, not
        // in memory: otherwise a login breaks when the callback lands on another pod.
        Integer pending = jdbc.queryForObject(
                "select count(*) from spring_session_attributes where attribute_name like '%AUTHORIZATION_REQUEST%'",
                Integer.class);
        assertTrue(pending != null && pending > 0, "no pending authorization request in SPRING_SESSION_ATTRIBUTES");

        // Log in at the IdP, then back through this service to the client's redirect URI.
        HttpResponse<String> afterLogin = browser.submitLogin(toLogin.response(),
                CompatEnvironment.KEYCLOAK_USER, CompatEnvironment.KEYCLOAK_PASSWORD);
        TestBrowser.Hop toClient = browser.followUntil(afterLogin, url -> url.startsWith(REDIRECT_URI));
        assertNotNull(toClient.stoppedAt(), "never reached the client; trail: " + toClient.trail()
                + "\nlast body: " + toClient.response().body());

        Map<String, String> callback = TestBrowser.queryParameters(toClient.stoppedAt());
        assertEquals(state, callback.get("state"));
        String code = callback.get("code");
        assertNotNull(code, toClient.stoppedAt());

        HttpResponse<String> token = api.postForm("/oauth2/token", CLIENT_ID, clientSecret,
                TestBrowser.encode(Map.of(
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", REDIRECT_URI,
                        "code_verifier", codeVerifier)));
        assertEquals(200, token.statusCode(), token.body());
        Map<String, Object> issued = jsonObject(token.body());
        assertNotNull(issued.get("access_token"));
        assertNotNull(issued.get("refresh_token"));
        assertNotNull(issued.get("id_token"));
        assertEquals("Bearer", issued.get("token_type"));
        return issued;
    }

    @Test
    @Order(2)
    @DisplayName("The issued access token identifies the federated, provisioned user")
    void accessTokenWorks() {
        HttpResponse<String> me = api.get("/api/users/me", (String) tokens.get("access_token"));

        assertEquals(200, me.statusCode(), me.body());
        assertEquals(CompatEnvironment.KEYCLOAK_EMAIL, jsonObject(me.body()).get("email"));
    }

    @Test
    @Order(3)
    @DisplayName("Refresh rotates the tokens; replaying the old refresh token is refused")
    void refresh() {
        String oldRefresh = (String) tokens.get("refresh_token");
        HttpResponse<String> response = api.postForm("/oauth2/token", CLIENT_ID, clientSecret,
                TestBrowser.encode(Map.of("grant_type", "refresh_token", "refresh_token", oldRefresh)));
        assertEquals(200, response.statusCode(), response.body());
        refreshed = jsonObject(response.body());
        assertNotNull(refreshed.get("access_token"));

        HttpResponse<String> me = api.get("/api/users/me", (String) refreshed.get("access_token"));
        assertEquals(200, me.statusCode(), me.body());

        HttpResponse<String> replay = api.postForm("/oauth2/token", CLIENT_ID, clientSecret,
                TestBrowser.encode(Map.of("grant_type", "refresh_token", "refresh_token", oldRefresh)));
        assertEquals(400, replay.statusCode(), replay.body());
        assertEquals("invalid_grant", jsonObject(replay.body()).get("error"));
    }

    @Test
    @Order(4)
    @DisplayName("RP-initiated logout ends the session and its authorizations, deletes the user's keys; its tokens stop working")
    void logout() throws Exception {
        // A fresh sign-in: the replay in refresh() revoked the first one on purpose.
        Map<String, Object> current = signIn();
        String idToken = (String) current.get("id_token");
        String accessToken = (String) current.get("access_token");
        // v3-11 (#19): the user has a permission key before logging out.
        String application = "OIDC_APP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        api.createApplication(application);
        String sub = subject(accessToken);
        // First login provisions a TEMPORARY user; an accepted invitation makes it ACTIVE.
        jdbc.update("UPDATE t_user SET status = 'ACTIVE' WHERE id = ?", sub);
        String key = "permissions:user:" + sub + ":app:" + application;
        HttpResponse<String> before = api.get("/api/users/me/permissions?applicationCode=" + application, accessToken);
        assertEquals(200, before.statusCode(), before.body());
        assertEquals(Boolean.TRUE, redis.hasKey(key), "the fetch wrote the key");
        assertEquals(1, thisSignIn(accessToken), "the sign-in has an authorization to revoke");

        TestBrowser browser = new TestBrowser();
        String logout = CompatEnvironment.ISSUER + "/connect/logout?id_token_hint=" + idToken
                + "&client_id=" + CLIENT_ID
                + "&post_logout_redirect_uri=" + URLEncoder.encode(POST_LOGOUT_URI, StandardCharsets.UTF_8);
        TestBrowser.Hop hop = browser.followUntil(browser.get(logout), url -> url.startsWith(POST_LOGOUT_URI));
        assertNotNull(hop.stoppedAt(), "logout did not return to the client; trail: " + hop.trail()
                + "\nlast: " + hop.response().statusCode() + " " + hop.response().body());

        HttpResponse<String> me = api.get("/api/users/me", accessToken);
        assertEquals(401, me.statusCode(), me.body());
        assertEquals(Boolean.FALSE, redis.hasKey(key), "logout deletes the user's keys");
        assertEquals(0, thisSignIn(accessToken), "logout revokes the authorization of this sign-in");
        assertEquals(401, api.get("/api/users/me/permissions?applicationCode=" + application, accessToken)
                .statusCode(), "the next miss is 401");
    }

    /**
     * The authorization behind this access token. Other sign-ins of the same
     * user (the first one in this class, on another "browser") keep theirs.
     */
    private int thisSignIn(String accessToken) {
        return jdbc.queryForObject("SELECT count(*) FROM oauth2_authorization WHERE access_token_value = ?",
                Integer.class, accessToken);
    }

    /** The {@code sub} claim of a JWT, read without verifying it (it came from this service). */
    private static String subject(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return (String) jsonObject(payload).get("sub");
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
