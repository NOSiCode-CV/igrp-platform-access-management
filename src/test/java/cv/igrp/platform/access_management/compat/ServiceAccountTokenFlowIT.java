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

import java.net.http.HttpResponse;
import java.util.Map;

import static cv.igrp.platform.access_management.compat.CompatSupport.jsonObject;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 migration gate — the machine-to-machine path every SDK depends
 * on, end to end through the real Authorization Server and Postgres:
 * client registration, service account, {@code client_credentials} token,
 * an M2M call, introspection and revocation.
 *
 * <p>Introspection and revocation read the {@code oauth2_authorization} row
 * back from Postgres, so this also proves the store's serialization format
 * round-trips through {@code JdbcOAuth2AuthorizationService}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceAccountTokenFlowIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;

    CompatSupport api;
    CompatSupport.M2MClient client;
    String accessToken;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        client = api.registerM2MClient("compat-token-flow");
    }

    @Test
    @Order(1)
    @DisplayName("Client registration returns the secret once")
    void secretReturnedOnCreation() {
        assertNotNull(client.clientSecret());
    }

    @Test
    @Order(2)
    @DisplayName("POST /oauth2/token issues a client_credentials token with the standard response fields")
    void clientCredentialsToken() {
        HttpResponse<String> response = api.postForm("/oauth2/token", client.clientId(), client.clientSecret(),
                "grant_type=client_credentials&scope=igrp.m2m");

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> token = jsonObject(response.body());
        accessToken = (String) token.get("access_token");
        assertNotNull(accessToken);
        assertEquals("Bearer", token.get("token_type"));
        assertNotNull(token.get("expires_in"));
        assertEquals("igrp.m2m", token.get("scope"));
    }

    @Test
    @Order(3)
    @DisplayName("The token is accepted on the M2M API (resource sync → 204)")
    void m2mSync() {
        HttpResponse<String> response = api.postJson("/api/m2m/sync/resources", accessToken,
                "{\"name\":\"compat-service\",\"type\":\"API\",\"description\":\"Resource for application: compat-service\"}");

        assertEquals(204, response.statusCode(), response.body());
    }

    @Test
    @Order(4)
    @DisplayName("The token is refused on a user-scoped path (403)")
    void userPathRejected() {
        HttpResponse<String> response = api.get("/api/users/me", accessToken);

        assertEquals(403, response.statusCode(), response.body());
    }

    @Test
    @Order(5)
    @DisplayName("Introspection reads the stored authorization back from Postgres")
    void introspection() {
        HttpResponse<String> response = api.postForm("/oauth2/introspect", client.clientId(), client.clientSecret(),
                "token=" + accessToken);

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> body = jsonObject(response.body());
        assertEquals(Boolean.TRUE, body.get("active"), response.body());
        assertEquals(client.clientId(), body.get("client_id"));
    }

    @Test
    @Order(6)
    @DisplayName("Revocation removes the authorization; introspection then reports inactive")
    void revocation() {
        HttpResponse<String> revoke = api.postForm("/oauth2/revoke", client.clientId(), client.clientSecret(),
                "token=" + accessToken);
        assertEquals(200, revoke.statusCode(), revoke.body());

        HttpResponse<String> introspect = api.postForm("/oauth2/introspect", client.clientId(), client.clientSecret(),
                "token=" + accessToken);
        assertEquals(200, introspect.statusCode());
        assertEquals(Boolean.FALSE, jsonObject(introspect.body()).get("active"), introspect.body());
    }
}
