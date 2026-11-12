package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-02 (#10), AM side: a service registers its routes at startup and each
 * route becomes an assignable permission. New keys get no assignments;
 * existing keys keep their name, description and assignments.
 */
class RouteRegistrationIT extends AbstractCompatIT {

    private static final String REGISTER = "/api/routes/register";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String service;
    private int applicationId;
    private String token;

    @BeforeEach
    void serviceWithItsOwnClient() {
        api = new CompatSupport(context);
        service = "svc-" + UUID.randomUUID().toString().substring(0, 8);
        applicationId = api.createApplication("APP_" + service.replace('-', '_').toUpperCase());
        token = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
    }

    @Test
    @DisplayName("A first registration creates every key, owned by the service and its client's application, with no assignments")
    void firstRegistrationCreatesKeys() {
        HttpResponse<String> response = register(token, "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null),
                route("POST", "/api/v1/faturas/{id}/aprovar", key("faturas.aprovar.update"), true,
                        "Aprovar fatura", "Aprova uma fatura pendente"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(CompatSupport.jsonObject(response.body()))
                .containsEntry("created", 2).containsEntry("unchanged", 0)
                .containsEntry("deleted", 0).containsEntry("renamed", 0);

        Map<String, Object> unnamed = permission(key("faturas.list"));
        assertThat(unnamed)
                .containsEntry("name", key("faturas.list"))
                .containsEntry("service", service)
                .containsEntry("application_id", applicationId)
                .containsEntry("last_seen_version", "1.0.0")
                .containsEntry("status", "ACTIVE");
        assertThat(permission(key("faturas.aprovar.update")))
                .containsEntry("name", "Aprovar fatura")
                .containsEntry("description", "Aprova uma fatura pendente");
        assertThat(assignments(key("faturas.list"))).isZero();
        assertThat(assignments(key("faturas.aprovar.update"))).isZero();
    }

    @Test
    @DisplayName("Legacy igrp.* permissions are left as they are")
    void legacyPermissionsAreUntouched() {
        List<Map<String, Object>> before = legacyPermissions();

        register(token, "1.0.0", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(legacyPermissions()).isEqualTo(before);
    }

    @Test
    @DisplayName("An older version registering after a newer one does not move the marker back")
    void olderVersionDoesNotMoveTheMarkerBack() {
        register(token, "1.1.0", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        HttpResponse<String> old = register(token, "1.0.9",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(old.statusCode()).as(old.body()).isEqualTo(200);
        assertThat(permission(key("faturas.list"))).containsEntry("last_seen_version", "1.1.0");
        assertThat(jdbc.queryForObject("SELECT last_version FROM t_registered_service WHERE service = ?",
                String.class, service)).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("A client that registers one service under another name renames it (#11)")
    void knownClientUnderANewNameRenames() {
        register(token, "1.0.0", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));
        String renamed = service + "-v2";

        HttpResponse<String> response = registerAs(renamed, token, "1.0.0",
                route("GET", "/api/v1/faturas", renamed + ".faturas.list", false, null, null));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("renamed", 1).containsEntry("created", 0);
        assertThat(count(renamed + ".faturas.list")).isOne();
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("The legacy igrp namespace and a null route are rejected")
    void reservedNamespaceAndMalformedRoutesAreRejected() {
        HttpResponse<String> igrpService = registerAs("igrp", token, "1.0.0",
                route("GET", "/api/v1/users", "igrp.users.list", false, null, null));
        HttpResponse<String> igrpName = register(token, "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), true, "igrp.users.list", null));
        HttpResponse<String> nullRoute = api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.0.0\",\"routes\":[null]}");

        assertThat(igrpService.statusCode()).as(igrpService.body()).isEqualTo(400);
        assertThat(igrpName.statusCode()).as(igrpName.body()).isEqualTo(400);
        assertThat(nullRoute.statusCode()).as(nullRoute.body()).isEqualTo(400);
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("A service account without igrp.m2m.sync cannot register routes")
    void serviceAccountWithoutSyncPermissionIsRefused() {
        String unprivileged = api.serviceToken(api.registerM2MClient(service + "-nosync", applicationId, List.of()));

        HttpResponse<String> response = register(unprivileged, "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("A later registration keeps an edited name and existing assignments, and only creates new keys")
    void laterRegistrationKeepsEditsAndAssignments() {
        register(token, "1.0.0", route("GET", "/api/v1/faturas", key("faturas.list"), true, "Listar", null));
        jdbc.update("UPDATE t_permission SET name = 'Ver faturas', description = 'Editado' WHERE permission_key = ?",
                key("faturas.list"));
        api.ensureBusinessData();
        jdbc.update("INSERT INTO t_role_permission (role_id, permission) "
                        + "SELECT (SELECT min(id) FROM t_role), id FROM t_permission WHERE permission_key = ?",
                key("faturas.list"));

        HttpResponse<String> second = register(token, "1.1.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), true, "Listar (do código)", "Do código"),
                route("DELETE", "/api/v1/faturas/{id}", key("faturas.delete"), false, null, null));

        assertThat(CompatSupport.jsonObject(second.body()))
                .containsEntry("created", 1).containsEntry("unchanged", 1);
        assertThat(permission(key("faturas.list")))
                .containsEntry("name", "Ver faturas")
                .containsEntry("description", "Editado")
                .containsEntry("last_seen_version", "1.1.0");
        assertThat(assignments(key("faturas.list"))).isOne();
        assertThat(permission(key("faturas.delete"))).containsEntry("last_seen_version", "1.1.0");
    }

    @Test
    @DisplayName("Each new key is audited once; a registration that creates nothing writes no audit row")
    void newKeysAreAudited() {
        register(token, "1.0.0", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));
        register(token, "1.0.1", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE entity_name = ? AND related_entity = ?
                   AND settings_entity_type = 'PERMISSION' AND settings_operation = 'CREATE'""",
                Long.class, key("faturas.list"), service)).isOne();
    }

    @Test
    @DisplayName("Another client cannot register routes under a service it does not own")
    void anotherClientCannotTakeOverAService() {
        register(token, "1.0.0", route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));
        String intruder = api.serviceToken(api.registerM2MClient(service + "-intruder", applicationId));

        HttpResponse<String> response = register(intruder, "1.0.0",
                route("GET", "/api/v1/notas", key("notas.list"), false, null, null));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(count(key("notas.list"))).isZero();
    }

    @Test
    @DisplayName("A key outside the service's prefix or with an unknown action rejects the whole registration")
    void invalidKeysAreRejected() {
        HttpResponse<String> foreignPrefix = register(token, "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null),
                route("GET", "/api/v1/outros", "other-service.outros.list", false, null, null));
        HttpResponse<String> unknownAction = register(token, "1.0.0",
                route("POST", "/api/v1/faturas/{id}/aprovar", key("faturas.approve"), true, null, null));

        assertThat(foreignPrefix.statusCode()).as(foreignPrefix.body()).isEqualTo(400);
        assertThat(unknownAction.statusCode()).as(unknownAction.body()).isEqualTo(400);
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("A client with no application cannot register: its keys could never be granted")
    void clientWithoutApplicationIsRefused() {
        String orphan = api.serviceToken(api.registerM2MClient(service + "-orphan", null));

        HttpResponse<String> response = register(orphan, "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("Only service accounts may register routes")
    void userTokensAreRefused() {
        HttpResponse<String> response = register(api.superadminToken(), "1.0.0",
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null));

        assertThat(response.statusCode()).as(response.body()).isIn(401, 403);
        assertThat(count(key("faturas.list"))).isZero();
    }

    @Test
    @DisplayName("Replicas registering at the same time create each key exactly once")
    void concurrentReplicasCreateEachKeyOnce() throws Exception {
        String[] routes = {
                route("GET", "/api/v1/faturas", key("faturas.list"), false, null, null),
                route("GET", "/api/v1/faturas/{id}", key("faturas.read"), false, null, null),
                route("POST", "/api/v1/faturas", key("faturas.create"), false, null, null)};
        List<Callable<HttpResponse<String>>> replicas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            replicas.add(() -> register(token, "1.0.0", routes));
        }

        int created = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (Future<HttpResponse<String>> result : pool.invokeAll(replicas)) {
                HttpResponse<String> response = result.get();
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                created += ((Number) CompatSupport.jsonObject(response.body()).get("created")).intValue();
            }
        }

        assertThat(created).isEqualTo(3);
        assertThat(count(key("faturas.list"))).isOne();
        assertThat(count(key("faturas.read"))).isOne();
        assertThat(count(key("faturas.create"))).isOne();
    }

    private String key(String suffix) {
        return service + "." + suffix;
    }

    private HttpResponse<String> register(String bearer, String version, String... routes) {
        return registerAs(service, bearer, version, routes);
    }

    private HttpResponse<String> registerAs(String serviceName, String bearer, String version, String... routes) {
        return api.postJson(REGISTER, bearer, "{\"service\":" + CompatSupport.q(serviceName)
                + ",\"version\":" + CompatSupport.q(version)
                + ",\"routes\":[" + String.join(",", routes) + "]}");
    }

    private static String route(String method, String path, String key, boolean annotated,
                                String name, String description) {
        return "{\"method\":" + CompatSupport.q(method) + ",\"path\":" + CompatSupport.q(path)
                + ",\"key\":" + CompatSupport.q(key) + ",\"annotated\":" + annotated
                + (name == null ? "" : ",\"name\":" + CompatSupport.q(name))
                + (description == null ? "" : ",\"description\":" + CompatSupport.q(description)) + "}";
    }

    private Map<String, Object> permission(String key) {
        return jdbc.queryForMap("""
                SELECT name, description, service, application_id, last_seen_version, status
                  FROM t_permission WHERE permission_key = ?""", key);
    }

    /** Role and department assignments of the key. */
    private long assignments(String key) {
        Long roles = jdbc.queryForObject("""
                SELECT count(*) FROM t_role_permission rp
                  JOIN t_permission p ON p.id = rp.permission WHERE p.permission_key = ?""", Long.class, key);
        Long departments = jdbc.queryForObject("""
                SELECT count(*) FROM t_permission_department pd
                  JOIN t_permission p ON p.id = pd.permission_id WHERE p.permission_key = ?""", Long.class, key);
        return (roles == null ? 0 : roles) + (departments == null ? 0 : departments);
    }

    private List<Map<String, Object>> legacyPermissions() {
        return jdbc.queryForList("""
                SELECT id, name, description, status, last_modified_date FROM t_permission
                 WHERE permission_key IS NULL ORDER BY id""");
    }

    private long count(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM t_permission WHERE permission_key = ?", Long.class, key);
    }
}
