package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 4 migration gate — no route fails open.
 *
 * <p>A broken permission check does not show up as an error: the call just
 * succeeds. So every route published in {@code /v3/api-docs} is called twice,
 * anonymously and as an existing, active user with no roles, and the status
 * codes are compared with {@code compat-baseline/authorization-matrix.txt}.
 * Path variables get a dummy value, so most guarded routes answer 401 or 403
 * before reaching a handler.
 *
 * <p>v3 baseline (#36), recorded deliberately: every AM route needs its key
 * (403 for the role-less user), except the self-service routes
 * ({@code SelfServicePaths}) and the public ones.
 *
 * <p>Re-record (only for a deliberate, reviewed change):
 * {@code mvn verify -Dit.test=AuthorizationMatrixIT -Dcontract.baseline.record=true}
 */
class AuthorizationMatrixIT extends AbstractCompatIT {

    private static final Path BASELINE = Paths.get("src", "test", "resources", "compat-baseline", "authorization-matrix.txt");
    private static final boolean RECORD = Boolean.getBoolean("contract.baseline.record");

    @Autowired
    ApplicationContext context;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    @DisplayName("Every route answers an anonymous caller and a role-less user as the v3 baseline records")
    @SuppressWarnings("unchecked")
    void matrix() throws Exception {
        CompatSupport api = new CompatSupport(context);
        api.ensureBusinessData();
        // v3 (#36): an existing, ACTIVE user with no roles, so a route that needs
        // a key answers 403 (an unknown user would be 401: AM does not authorize it).
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String rolelessId = UUID.randomUUID().toString();
        String rolelessEmail = "roleless-" + rolelessId + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Roleless User', 'ACTIVE')""", rolelessId, rolelessEmail, rolelessEmail);

        Map<String, Object> paths = (Map<String, Object>) CompatSupport.jsonObject(
                api.get("/v3/api-docs", null).body()).get("paths");

        TreeSet<String> lines = new TreeSet<>();
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            for (String method : ((Map<String, Object>) path.getValue()).keySet()) {
                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                // A fresh session per route: POST /api/session/close ends the one it runs on.
                String nobody = api.userToken(rolelessId, rolelessEmail);
                lines.add(method.toUpperCase() + " " + path.getKey()
                        + " anonymous=" + status(method, url, null)
                        + " roleless=" + status(method, url, nobody));
            }
        }

        if (RECORD) {
            Files.writeString(BASELINE, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(BASELINE), "no baseline; record with -Dcontract.baseline.record=true");
        List<String> expected = Files.readAllLines(BASELINE, StandardCharsets.UTF_8);
        assertEquals(String.join("\n", expected), String.join("\n", new ArrayList<>(lines)), "authorization matrix changed");
    }

    private int status(String method, String path, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(CompatEnvironment.ISSUER + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString("get".equals(method) ? "" : "{}"));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
