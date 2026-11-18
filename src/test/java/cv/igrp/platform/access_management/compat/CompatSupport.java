package cv.igrp.platform.access_management.compat;

import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-side helpers for the compat suite: user tokens that pass the real
 * filter chain, and a plain JDK HTTP client.
 *
 * <p>User tokens are signed with the application's own {@link JwtEncoder} (same
 * key and {@code kid} as tokens it issues at {@code /oauth2/token}) and are
 * backed by an active authorization in {@code oauth2_authorization}, so AM
 * treats them as signed in exactly as it would after a login.
 *
 * <p>JSON is parsed with Spring Boot's {@link JsonParser} and written by hand,
 * so the suite does not depend on Jackson 2 or Jackson 3 and runs unchanged
 * across the migration.
 */
public final class CompatSupport {

    private static final JsonParser JSON = JsonParserFactory.getJsonParser();

    private final JwtEncoder jwtEncoder;
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    public CompatSupport(ApplicationContext context) {
        this.jwtEncoder = context.getBean(JwtEncoder.class);
        this.jdbc = context.getBean(JdbcTemplate.class);
    }

    // ---- tokens ----

    /**
     * A token for the bootstrapped superadmin. The JWT {@code sub} is the
     * internal user id, which {@code ConfigurationService} generates; the
     * configured external id is stored as the username.
     */
    public String superadminToken() {
        return userToken(superadminId(), "superadmin@igrp.cv");
    }

    /**
     * A user access token with an active authorization behind it in AM's store, as a sign-in
     * leaves: the user is signed in (#33).
     */
    public String userToken(String userId, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(CompatEnvironment.ISSUER)
                .subject(userId)
                .audience(List.of("igrp-access-management"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("name", "Compat Test User")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        int signedIn = jdbc.update("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                                                  access_token_value, access_token_issued_at, access_token_expires_at,
                                                  access_token_type)
                VALUES (?, 'compat-client', ?, 'authorization_code', ?, ?, ?, 'Bearer')""",
                UUID.randomUUID().toString(), userId, token, Timestamp.from(now), Timestamp.from(now.plusSeconds(900)));
        if (signedIn != 1) {
            throw new IllegalStateException("the test sign-in was not stored");
        }
        return token;
    }

    public static final String BUSINESS_DEPARTMENT = "COMPAT_DEPT";
    public static final String BUSINESS_ROLE = "COMPAT_ROLE";

    /**
     * Business data created through the public API: a department, a role in
     * it, and the superadmin holding that role. The bootstrapped system
     * department ({@code DEPT_IGRP}) is deliberately hidden from M2M lookups, so
     * SDK lookups need data of their own. Idempotent, because every compat test
     * class shares one database.
     */
    public synchronized void ensureBusinessData() {
        Integer existing = jdbc.queryForObject("select count(*) from t_department where code = ?", Integer.class,
                BUSINESS_DEPARTMENT);
        if (existing != null && existing > 0) {
            return;
        }
        String admin = superadminToken();
        expectSuccess(postJson("/api/departments", admin,
                "{\"code\":" + q(BUSINESS_DEPARTMENT) + ",\"name\":\"Compat Department\",\"status\":\"ACTIVE\"}"));
        expectSuccess(postJson("/api/departments/" + BUSINESS_DEPARTMENT + "/roles", admin,
                "{\"code\":" + q(BUSINESS_ROLE) + ",\"name\":\"Compat Role\",\"status\":\"ACTIVE\"}"));
        expectSuccess(postJson("/api/users/" + superadminId() + "/departments/" + BUSINESS_DEPARTMENT + "/roles", admin,
                "{\"roles\":[" + q(BUSINESS_ROLE) + "]}"));
    }

    public String superadminId() {
        return jdbc.queryForObject("select id from t_user where username = ?", String.class,
                CompatEnvironment.SUPERADMIN_ID);
    }

    private static void expectSuccess(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(response.request().method() + " " + response.request().uri()
                    + " -> " + response.statusCode() + " " + response.body());
        }
    }

    // ---- M2M clients ----

    /** What an SDK consumer is configured with: client id, secret and scope. */
    public record M2MClient(String clientId, String clientSecret, String scope) {
    }

    /**
     * Registers a {@code client_credentials} client through the API, the way an
     * operator does from the Application Center, and binds a service account
     * to it with the permissions the SDK's M2M calls require.
     */
    public M2MClient registerM2MClient(String clientId) {
        return registerM2MClient(clientId, null);
    }

    /** As {@link #registerM2MClient(String)}, with the client owned by an application (null for none). */
    public M2MClient registerM2MClient(String clientId, Integer applicationId) {
        return registerM2MClient(clientId, applicationId, M2M_KEYS);
    }

    /**
     * What the SDK's M2M calls and the starter's route registration need (#36):
     * the keys of AM's routes that were guarded by igrp.m2m.sync, igrp.users.list,
     * igrp.departments.list and igrp.departments.view.
     */
    public static final List<String> M2M_KEYS = List.of(
            "access-management-api.m2m.sync.applications.create",
            "access-management-api.m2m.sync.applications.menus.create",
            "access-management-api.m2m.sync.permissions.create",
            "access-management-api.m2m.sync.resources.create",
            "access-management-api.m2m.global-configuration.list",
            "access-management-api.m2m.users.create",
            "access-management-api.m2m.departments.create",
            "access-management-api.m2m.roles.create",
            "access-management-api.routes.register.create",
            "access-management-api.manifests.register.create");

    /** As {@link #registerM2MClient(String, Integer)}, with exactly these permissions (keys or legacy names). */
    public M2MClient registerM2MClient(String clientId, Integer applicationId, List<String> permissionNames) {
        String admin = superadminToken();
        HttpResponse<String> created = postJson("/api/clients", admin,
                "{\"clientId\":" + q(clientId) + ",\"clientName\":" + q(clientId + " (compat)") + ","
                        + (applicationId == null ? "" : "\"applicationId\":" + applicationId + ",")
                        + "\"scopes\":[\"igrp.m2m\"],\"grantTypes\":[\"client_credentials\"]}");
        if (created.statusCode() != 200 && created.statusCode() != 201) {
            throw new IllegalStateException("client registration failed: " + created.statusCode() + " " + created.body());
        }
        Map<String, Object> client = jsonObject(created.body());

        List<Integer> permissionIds = permissionNames.isEmpty() ? List.of() : jdbc.queryForList(
                "select id from t_permission where coalesce(permission_key, name) in ("
                        + String.join(",", permissionNames.stream().map(n -> "?").toList()) + ")",
                Integer.class, permissionNames.toArray());
        HttpResponse<String> account = postJson("/api/service-accounts", admin,
                "{\"name\":" + q(clientId + " SA") + ",\"oauthClientId\":" + q((String) client.get("id"))
                        + ",\"permissionIds\":" + permissionIds + "}");
        if (account.statusCode() != 200 && account.statusCode() != 201) {
            throw new IllegalStateException("service account creation failed: " + account.statusCode() + " " + account.body());
        }
        return new M2MClient(clientId, (String) client.get("clientSecret"), "igrp.m2m");
    }

    /** Creates an internal application through the API and returns its id. */
    public int createApplication(String code) {
        HttpResponse<String> created = postJson("/api/applications", superadminToken(),
                "{\"code\":" + q(code) + ",\"name\":" + q("App " + code)
                        + ",\"type\":\"INTERNAL\",\"status\":\"ACTIVE\"}");
        if (created.statusCode() != 200 && created.statusCode() != 201) {
            throw new IllegalStateException("application creation failed: " + created.statusCode() + " " + created.body());
        }
        return ((Number) jsonObject(created.body()).get("id")).intValue();
    }

    /** A client_credentials access token for this client, as a service gets one at startup. */
    public String serviceToken(M2MClient client) {
        HttpResponse<String> token = postForm("/oauth2/token", client.clientId(), client.clientSecret(),
                "grant_type=client_credentials&scope=" + client.scope());
        return (String) jsonObject(token.body()).get("access_token");
    }

    // ---- HTTP ----

    public HttpResponse<String> get(String path, String bearer) {
        return send(request(path, bearer).GET().build());
    }

    /** A GET with extra headers; a repeated {@code Accept} replaces the JSON default. */
    public HttpResponse<String> get(String path, String bearer, Map<String, String> headers) {
        HttpRequest.Builder builder = request(path, bearer);
        headers.forEach(builder::setHeader);
        return send(builder.GET().build());
    }

    public HttpResponse<String> postJson(String path, String bearer, String json) {
        return send(request(path, bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    /** A JSON POST with extra headers (for example {@code X-Correlation-ID}). */
    public HttpResponse<String> postJson(String path, String bearer, String json, Map<String, String> headers) {
        HttpRequest.Builder builder = request(path, bearer).header("Content-Type", "application/json");
        headers.forEach(builder::setHeader);
        return send(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    public HttpResponse<String> putJson(String path, String bearer, String json) {
        return send(request(path, bearer)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    public HttpResponse<String> delete(String path, String bearer) {
        return send(request(path, bearer).DELETE().build());
    }

    /** A DELETE with a JSON body, as several AM endpoints take the codes to remove in the body. */
    public HttpResponse<String> deleteJson(String path, String bearer, String json) {
        return send(request(path, bearer)
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json)).build());
    }

    /** A form POST with HTTP Basic client authentication, as OAuth2 clients send it. */
    public HttpResponse<String> postForm(String path, String clientId, String clientSecret, String form) {
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder(URI.create(CompatEnvironment.ISSUER + path))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    private HttpRequest.Builder request(String path, String bearer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(CompatEnvironment.ISSUER + path))
                .header("Accept", "application/json");
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ---- JSON ----

    public static Map<String, Object> jsonObject(String json) {
        return JSON.parseMap(json);
    }

    public static List<Object> jsonArray(String json) {
        return JSON.parseList(json);
    }

    /** Quotes and escapes a string for hand-written JSON. */
    public static String q(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
