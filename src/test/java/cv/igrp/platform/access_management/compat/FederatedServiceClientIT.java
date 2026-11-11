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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static cv.igrp.platform.access_management.compat.CompatSupport.jsonObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v3-17 / v3-18 (#24, #26): a service client lives in Keycloak. AM creates, rotates and
 * deletes it through the adapter, keeps no secret, checks presented credentials with
 * Keycloak and issues its own token with the service account as {@code sub}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedServiceClientIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;

    CompatSupport api;
    JdbcTemplate jdbc;
    CompatSupport.M2MClient client;
    String oldSecret;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        jdbc = context.getBean(JdbcTemplate.class);
        client = api.registerM2MClient("compat-federated");
    }

    @Test
    @Order(1)
    @DisplayName("Create: the client is in Keycloak, AM's row has the IdP and no secret")
    void createdInKeycloak() {
        Map<String, Object> row = jdbc.queryForMap(
                "select client_secret, idp from t_oauth_client where client_id = ?", client.clientId());
        assertNull(row.get("client_secret"));
        assertEquals("keycloak", row.get("idp"));
        assertEquals(200, keycloakToken(client.clientId(), client.clientSecret()).statusCode());
    }

    @Test
    @Order(2)
    @DisplayName("Token: AM accepts the IdP credentials and issues its own token for the service account")
    void amToken() {
        HttpResponse<String> response = api.postForm("/oauth2/token", client.clientId(), client.clientSecret(),
                "grant_type=client_credentials&scope=igrp.m2m");

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> claims = claims((String) jsonObject(response.body()).get("access_token"));
        assertEquals(CompatEnvironment.ISSUER, claims.get("iss"));
        String serviceAccount = JdbcClient.create(jdbc).sql("select sa.id::text from t_service_account sa "
                        + "join t_oauth_client c on c.id = sa.oauth_client_id where c.client_id = ?")
                .param(client.clientId()).query(String.class).single();
        assertEquals(serviceAccount, claims.get("sub"));
    }

    @Test
    @Order(3)
    @DisplayName("Token: a wrong secret is refused with invalid_client")
    void wrongSecret() {
        HttpResponse<String> response = api.postForm("/oauth2/token", client.clientId(), "not-the-secret",
                "grant_type=client_credentials&scope=igrp.m2m");

        assertEquals(401, response.statusCode(), response.body());
        assertTrue(response.body().contains("invalid_client"), response.body());
    }

    @Test
    @Order(4)
    @DisplayName("The secret is nowhere in AM's database")
    void secretNotStored() {
        List<String> found = columnsContaining(client.clientSecret());
        assertTrue(found.isEmpty(), "secret found in " + found);
    }

    @Test
    @Order(5)
    @DisplayName("Rotate: Keycloak makes a new secret, returned once; the old one stops working in AM")
    void rotate() {
        oldSecret = client.clientSecret();
        HttpResponse<String> rotated = api.postJson("/api/clients/" + clientRowId(client.clientId()) + "/rotate-secret",
                api.superadminToken(), "");
        assertEquals(200, rotated.statusCode(), rotated.body());
        String newSecret = (String) jsonObject(rotated.body()).get("clientSecret");
        assertNotEquals(oldSecret, newSecret);

        assertEquals(401, api.postForm("/oauth2/token", client.clientId(), oldSecret,
                "grant_type=client_credentials&scope=igrp.m2m").statusCode());
        assertEquals(200, api.postForm("/oauth2/token", client.clientId(), newSecret,
                "grant_type=client_credentials&scope=igrp.m2m").statusCode());
        assertTrue(columnsContaining(newSecret).isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("Delete: the client leaves AM and Keycloak")
    void delete() {
        String admin = api.superadminToken();
        HttpResponse<String> created = api.postJson("/api/clients", admin,
                "{\"clientId\":\"compat-federated-gone\",\"clientName\":\"Gone\","
                        + "\"scopes\":[\"igrp.m2m\"],\"grantTypes\":[\"client_credentials\"]}");
        assertEquals(201, created.statusCode(), created.body());
        String secret = (String) jsonObject(created.body()).get("clientSecret");

        HttpResponse<String> deleted = api.delete("/api/clients/" + jsonObject(created.body()).get("id"), admin);

        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals(0, jdbc.queryForObject("select count(*) from t_oauth_client where client_id = 'compat-federated-gone'",
                Integer.class));
        assertEquals(401, keycloakToken("compat-federated-gone", secret).statusCode());
    }

    @Test
    @Order(7)
    @DisplayName("Delete: a client bound to a service account is refused and stays in Keycloak")
    void deleteBound() {
        String rowId = clientRowId(client.clientId());
        HttpResponse<String> deleted = api.delete("/api/clients/" + rowId, api.superadminToken());

        assertEquals(409, deleted.statusCode(), deleted.body());
        HttpResponse<String> rotated = api.postJson("/api/clients/" + rowId + "/rotate-secret", api.superadminToken(), "");
        assertEquals(200, keycloakToken(client.clientId(), (String) jsonObject(rotated.body()).get("clientSecret"))
                .statusCode());
    }

    private String clientRowId(String clientId) {
        return jdbc.queryForObject("select id::text from t_oauth_client where client_id = ?", String.class, clientId);
    }

    /** Every text-like column of every table in the schema whose value contains {@code needle}. */
    private List<String> columnsContaining(String needle) {
        List<Map<String, Object>> columns = jdbc.queryForList("select table_name, column_name, data_type "
                + "from information_schema.columns where table_schema = 'public' "
                + "and data_type in ('text', 'character varying', 'jsonb', 'json', 'bytea')");
        List<String> found = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String name = "\"" + column.get("column_name") + "\"";
            String value = "bytea".equals(column.get("data_type"))
                    ? "encode(" + name + ", 'escape')" : name + "::text";
            Integer hits = jdbc.queryForObject("select count(*) from \"" + column.get("table_name") + "\" where "
                    + value + " like ?", Integer.class, "%" + needle + "%");
            if (hits != null && hits > 0) {
                found.add(column.get("table_name") + "." + column.get("column_name"));
            }
        }
        return found;
    }

    private static HttpResponse<String> keycloakToken(String clientId, String secret) {
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(CompatEnvironment.keycloakIssuer()
                                    + "/protocol/openid-connect/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> claims(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return jsonObject(payload);
    }
}
