package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 4 migration gate — the HTTP contract as clients see it: status
 * code, the headers clients branch on, and the exact JSON body with its key
 * order, recorded as golden files under {@code src/test/resources/contract-baseline}.
 *
 * <p>Covers the error shapes every client parses (RFC 7807 bodies for 400, 401,
 * 403 and 404, OAuth2 error bodies), representative success bodies whose content
 * this suite controls, and the discovery documents. Values that legitimately
 * change between runs are normalized before comparison: UUIDs, timestamps, the
 * random port and numeric ids. Nothing else is.
 *
 * <p>The golden files were recorded on Spring Boot 3.5. After the migration they
 * must match unchanged; any difference is either a regression or a deliberate
 * contract change that is recorded in the migration plan first.
 *
 * <p>Re-record (only for a deliberate, reviewed contract change):
 * {@code mvn test -Dtest=HttpContractBaselineIT -Dcontract.baseline.record=true}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpContractBaselineIT extends AbstractCompatIT {

    private static final Path BASELINE_DIR = Paths.get("src", "test", "resources", "contract-baseline");
    private static final boolean RECORD = Boolean.getBoolean("contract.baseline.record");
    private static final List<String> PINNED_HEADERS = List.of("content-type", "www-authenticate", "cache-control");

    @Autowired
    ApplicationContext context;

    CompatSupport api;
    String admin;
    String unprivileged;
    CompatSupport.M2MClient m2m;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        api.ensureBusinessData();
        admin = api.superadminToken();
        // A valid token and live session for a subject with no roles at all.
        unprivileged = api.userToken(UUID.randomUUID().toString(), "nobody@example.cv");
        m2m = api.registerM2MClient("compat-contract");
    }

    private record Case(String name, Function<HttpContractBaselineIT, HttpResponse<String>> call) {
    }

    private List<Case> cases() {
        String dept = CompatSupport.BUSINESS_DEPARTMENT;
        String role = CompatSupport.BUSINESS_ROLE;
        return List.of(
                // ---- discovery and OAuth2 protocol surface (SDK token provider, gateways) ----
                new Case("oidc-discovery", t -> t.api.get("/.well-known/openid-configuration", null)),
                new Case("oauth-authorization-server-metadata", t -> t.api.get("/.well-known/oauth-authorization-server", null)),
                // Behind the gateway: the chain matchers must still recognise these paths.
                new Case("forwarded-prefix-discovery", t -> t.api.get("/.well-known/openid-configuration", null,
                        java.util.Map.of("X-Forwarded-Prefix", "/am"))),
                new Case("forwarded-prefix-jwks", t -> t.api.get("/oauth2/jwks", null,
                        java.util.Map.of("X-Forwarded-Prefix", "/am"))),
                new Case("token-invalid-client", t -> t.api.postForm("/oauth2/token", "no-such-client", "wrong",
                        "grant_type=client_credentials")),
                new Case("token-unsupported-grant", t -> t.api.postForm("/oauth2/token", t.m2m.clientId(), t.m2m.clientSecret(),
                        "grant_type=password&username=a&password=b")),
                new Case("token-invalid-scope", t -> t.api.postForm("/oauth2/token", t.m2m.clientId(), t.m2m.clientSecret(),
                        "grant_type=client_credentials&scope=not-granted")),

                // ---- authentication and authorization failures ----
                new Case("no-token", t -> t.api.get("/api/users/me", null)),
                new Case("malformed-token", t -> t.api.get("/api/users/me", "not-a-jwt")),
                new Case("forbidden", t -> t.api.get("/api/departments/by-code/" + dept, t.unprivileged)),
                new Case("actuator-health", t -> t.api.get("/actuator/health", null)),

                // ---- error bodies (GlobalExceptionHandler) ----
                new Case("not-found-department", t -> t.api.get("/api/departments/by-code/NO_SUCH_DEPT", t.admin)),
                new Case("not-found-role", t -> t.api.get("/api/roles/by-code/NO_SUCH_ROLE?departmentCode=" + dept, t.admin)),
                // Not handled by GlobalExceptionHandler: Spring Boot's own /error body.
                new Case("missing-request-param", t -> t.api.get("/api/roles/by-code/" + role, t.admin)),
                new Case("validation-error", t -> t.api.postJson("/api/departments", t.admin, "{}")),
                new Case("malformed-json", t -> t.api.postJson("/api/departments", t.admin, "{\"code\":")),
                new Case("wrong-type", t -> t.api.postJson("/api/departments", t.admin,
                        "{\"code\":\"X\",\"name\":\"X\",\"status\":\"NOT_A_STATUS\"}")),
                new Case("m2m-users-without-filter", t -> {
                    String token = t.m2mToken();
                    return t.api.postJson("/api/m2m/users", token, "[]");
                }),

                // ---- success bodies over data this suite owns ----
                new Case("department-by-code", t -> t.api.get("/api/departments/by-code/" + dept, t.admin)),
                // jackson-dataformat-xml on the classpath enables XML negotiation.
                new Case("department-by-code-xml", t -> t.api.get("/api/departments/by-code/" + dept, t.admin,
                        java.util.Map.of("Accept", "application/xml"))),
                new Case("role-by-code", t -> t.api.get("/api/roles/by-code/" + role + "?departmentCode=" + dept, t.admin)),
                new Case("users-me", t -> t.api.get("/api/users/me", t.admin)),
                new Case("users-me-departments", t -> t.api.get("/api/users/me/departments", t.admin)),
                new Case("authorize-check", t -> t.api.postJson("/api/authorize/check", t.admin,
                        "{\"resource\":\"igrp-access-management\",\"action\":\"igrp.users.list\"}")),
                new Case("authorize-batch-check", t -> t.api.postJson("/api/authorize/batch-check", t.admin,
                        "[{\"resource\":\"igrp-access-management\",\"action\":\"igrp.users.list\"},"
                                + "{\"resource\":\"igrp-access-management\",\"action\":\"igrp.no.such.permission\"}]")),
                new Case("m2m-departments", t -> t.api.postJson("/api/m2m/departments?activeOnly=true", t.m2mToken(),
                        "[\"" + dept + "\"]")),
                new Case("m2m-roles", t -> t.api.postJson("/api/m2m/roles?activeOnly=true", t.m2mToken(),
                        "[\"" + role + "\"]"))
        );
    }

    private String m2mToken() {
        HttpResponse<String> token = api.postForm("/oauth2/token", m2m.clientId(), m2m.clientSecret(),
                "grant_type=client_credentials&scope=" + m2m.scope());
        return (String) CompatSupport.jsonObject(token.body()).get("access_token");
    }

    @TestFactory
    Stream<DynamicTest> contractMatchesBaseline() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> verify(c)));
    }

    private void verify(Case c) throws Exception {
        HttpResponse<String> response = c.call().apply(this);
        String actual = render(response);
        Path golden = BASELINE_DIR.resolve(c.name() + ".txt");

        if (RECORD) {
            Files.createDirectories(BASELINE_DIR);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(golden), "no baseline for " + c.name()
                + "; record one with -Dcontract.baseline.record=true");
        assertEquals(Files.readString(golden, StandardCharsets.UTF_8), actual, "HTTP contract changed: " + c.name());
    }

    /** Status line, pinned headers in a fixed order, blank line, normalized body. */
    private static String render(HttpResponse<String> response) {
        StringBuilder out = new StringBuilder("HTTP ").append(response.statusCode()).append('\n');
        for (String header : PINNED_HEADERS) {
            response.headers().firstValue(header)
                    .ifPresent(value -> out.append(header).append(": ").append(normalize(value)).append('\n'));
        }
        return out.append('\n').append(sortValidationErrors(normalize(response.body()))).append('\n').toString();
    }

    private static final java.util.regex.Pattern ERRORS_OBJECT =
            java.util.regex.Pattern.compile("\"errors\":\\{([^{}]*)}");

    /**
     * The field-error map of a validation ProblemDetail comes back in no stable
     * order (the server builds it from a hash map), so its entries are sorted.
     * Clients read it by field name; order was never part of the contract.
     */
    static String sortValidationErrors(String body) {
        java.util.regex.Matcher matcher = ERRORS_OBJECT.matcher(body);
        if (!matcher.find()) {
            return body;
        }
        String sorted = java.util.Arrays.stream(matcher.group(1).split(",(?=\")"))
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return body.substring(0, matcher.start(1)) + sorted + body.substring(matcher.end(1));
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("localhost:" + CompatEnvironment.PORT, "localhost:<port>")
                .replace(CompatEnvironment.keycloakIssuer(), "<external-idp>")
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?", "<datetime>")
                .replaceAll("\"(id|[a-zA-Z]+Id|resolutionTimeMs)\":\\d+", "\"$1\":<n>")
                .replaceAll("<id>\\d+</id>", "<id><n></id>");
    }
}
