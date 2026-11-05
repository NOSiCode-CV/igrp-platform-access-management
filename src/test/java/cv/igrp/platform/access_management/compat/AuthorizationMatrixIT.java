package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

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
 * anonymously and as a valid user with no roles, and the status codes are
 * compared with {@code compat-baseline/authorization-matrix.txt}, recorded on
 * Spring Boot 3.5. Path variables get a dummy value, so most guarded routes
 * answer 401 or 403 before reaching a handler.
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
    @DisplayName("Every route answers an anonymous caller and a role-less user as it did on Boot 3.5")
    @SuppressWarnings("unchecked")
    void matrix() throws Exception {
        CompatSupport api = new CompatSupport(context);
        api.ensureBusinessData();

        Map<String, Object> paths = (Map<String, Object>) CompatSupport.jsonObject(
                api.get("/v3/api-docs", null).body()).get("paths");

        TreeSet<String> lines = new TreeSet<>();
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            for (String method : ((Map<String, Object>) path.getValue()).keySet()) {
                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                // A fresh session per route: POST /api/session/close ends the one it runs on.
                String nobody = api.userToken(UUID.randomUUID().toString(), "nobody@example.cv");
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
