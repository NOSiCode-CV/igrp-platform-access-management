package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-13 (#21): each permission assigned to a role carries a row-visibility
 * scope ({@code all}, {@code own}, {@code by_role}, {@code department}). The
 * permission fetch returns it; a change evicts the affected keys after commit.
 */
class RolePermissionScopeIT extends AbstractCompatIT {

    private static final String FETCH = "/api/users/me/permissions?applicationCode=";
    private static final String MIGRATION = "db/migration/V23_1__role_permission_scope.sql";

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
    private int appId;
    private String userId;
    private String email;

    @BeforeEach
    void roleWithTwoPermissions() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "sc-" + suffix.toLowerCase();
        app = "SC_APP_" + suffix;
        appId = api.createApplication(app);
        String serviceToken = api.serviceToken(api.registerM2MClient(service + "-client", appId));
        ok(api.postJson("/api/routes/register", serviceToken,
                "{\"service\":" + CompatSupport.q(service) + ",\"version\":\"1.0.0\",\"routes\":["
                        + route("GET", "/api/v1/faturas", key("list")) + ","
                        + route("POST", "/api/v1/faturas", key("create")) + ","
                        + route("DELETE", "/api/v1/faturas/{id}", key("delete")) + "]}"));

        userId = UUID.randomUUID().toString();
        email = "sc-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Scope User', 'ACTIVE')""", userId, email, email);

        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept())
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept() + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        ok(api.postJson("/api/departments/" + dept() + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "," + CompatSupport.q(key("create")) + ","
                        + CompatSupport.q(key("delete")) + "]"));
        createRole("R1");
        grant("R1", "list", "create");
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept() + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role("R1")) + "]}"));
    }

    @AfterEach
    void retireDepartment() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", dept());
    }

    @Test
    @DisplayName("A permission added to a role gets the department scope, shown in the role and in the fetch")
    void newGrantDefaultsToDepartment() {
        assertThat(rolePermissionScopes("R1"))
                .containsEntry(key("list"), "department").containsEntry(key("create"), "department");
        assertThat(fetchScopes())
                .containsEntry(key("list"), "department").containsEntry(key("create"), "department");
        assertThat(redis.<String, String>opsForHash().entries(setKey(userId) + ":scopes"))
                .containsEntry(key("list"), "department");
    }

    @Test
    @DisplayName("Scopes are set per role in one call; the fetch returns them and the key is evicted after commit")
    void setScopesInBulk() {
        fetchScopes();
        assertThat(redis.hasKey(setKey(userId))).isTrue();

        HttpResponse<String> response = setScopes("R1",
                "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"},"
                        + "{\"key\":" + CompatSupport.q(key("create")) + ",\"scope\":\"by_role\"}]");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(redis.hasKey(setKey(userId))).as("evicted after the change committed").isFalse();
        assertThat(rolePermissionScopes("R1"))
                .containsEntry(key("list"), "own").containsEntry(key("create"), "by_role");
        assertThat(fetchScopes())
                .containsEntry(key("list"), "own").containsEntry(key("create"), "by_role");
        assertThat(auditedChange(key("list"))).containsExactly("department", "own");
    }

    @Test
    @DisplayName("Setting a scope to its current value changes nothing and evicts nothing")
    void unchangedScopeIsANoOp() {
        fetchScopes();

        ok(setScopes("R1", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"department\"}]"));

        assertThat(redis.hasKey(setKey(userId))).isTrue();
        assertThat(auditedChange(key("list"))).isEmpty();
    }

    @Test
    @DisplayName("An invalid scope is refused and nothing changes")
    void invalidScopeRefused() {
        HttpResponse<String> response = setScopes("R1",
                "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"},"
                        + "{\"key\":" + CompatSupport.q(key("create")) + ",\"scope\":\"everything\"}]");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(rolePermissionScopes("R1")).containsEntry(key("list"), "department");
        assertThat(setScopes("R1", "[null]").statusCode()).isEqualTo(400);
        assertThat(setScopes("R1", "[{\"key\":\"\",\"scope\":\"own\"}]").statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("A key the role does not hold is refused and nothing changes")
    void keyNotOnTheRoleRefused() {
        HttpResponse<String> response = setScopes("R1",
                "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"},"
                        + "{\"key\":" + CompatSupport.q(key("delete")) + ",\"scope\":\"own\"}]");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(rolePermissionScopes("R1")).containsEntry(key("list"), "department");
    }

    @Test
    @DisplayName("Adding or removing another permission keeps the scopes already set")
    void otherGrantChangesKeepScopes() {
        ok(setScopes("R1", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"}]"));

        grant("R1", "delete");
        ok(api.deleteJson("/api/departments/" + dept() + "/roles/" + role("R1") + "/permissions", admin,
                "[" + CompatSupport.q(key("create")) + "]"));

        assertThat(rolePermissionScopes("R1"))
                .containsEntry(key("list"), "own").containsEntry(key("delete"), "department")
                .doesNotContainKey(key("create"));
    }

    @Test
    @DisplayName("A service account's scopes are the union of its roles; a direct grant is all")
    void serviceAccountUnion() {
        createRole("R2");
        grant("R2", "list", "delete");
        ok(setScopes("R1", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"}]"));
        ok(setScopes("R2", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"by_role\"},"
                + "{\"key\":" + CompatSupport.q(key("delete")) + ",\"scope\":\"own\"}]"));
        CompatSupport.M2MClient client = api.registerM2MClient(service + "-caller", null, List.of(key("delete")));
        String serviceAccountId = jdbc.queryForList("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                 WHERE oc.client_id = ?""", String.class, client.clientId()).getFirst();
        for (String r : List.of("R1", "R2")) {
            jdbc.update("""
                    INSERT INTO t_service_account_role_assignment (service_account_id, role_id, assigned_at)
                    SELECT ?::uuid, id, now() FROM t_role WHERE code = ?""", serviceAccountId, role(r));
        }

        String serviceToken = api.serviceToken(client);
        Map<String, Object> body = fetch(serviceToken);

        assertThat(scopes(body))
                .containsEntry(key("list"), "by_role,own")
                .as("the direct grant is all, which wins over own").containsEntry(key("delete"), "all")
                .containsEntry(key("create"), "department");

        ok(setScopes("R1", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"all\"}]"));
        assertThat(redis.hasKey(setKey(serviceAccountId))).as("the service account's set is evicted too").isFalse();
        assertThat(scopes(fetch(serviceToken))).containsEntry(key("list"), "all");
    }

    @Test
    @DisplayName("Root gets every key with scope all; its role's scopes cannot be set")
    void rootIsAll() {
        assertThat(scopes(fetch(admin))).containsOnly(
                Map.entry(key("list"), "all"), Map.entry(key("create"), "all"), Map.entry(key("delete"), "all"));

        HttpResponse<String> response = api.putJson(
                "/api/departments/DEPT_IGRP/roles/DEPT_IGRP.superadmin/permissions/scopes", admin,
                "[{\"key\":\"igrp.x\",\"scope\":\"own\"}]");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    }

    @Test
    @DisplayName("The migration converts rows without a scope (all for root, department otherwise) and a rerun keeps set scopes")
    void migrationConvertsAndIsIdempotent() throws IOException {
        String sql = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        ok(setScopes("R1", "[{\"key\":" + CompatSupport.q(key("list")) + ",\"scope\":\"own\"}]"));
        // Rows as a 0.2 database has them, without a scope: one on root's role, one on R1.
        jdbc.execute("ALTER TABLE t_role_permission ALTER COLUMN scope DROP NOT NULL");
        try {
            jdbc.update("UPDATE t_role_permission SET scope = NULL WHERE role_id = ? AND permission = ?",
                    roleId("R1"), permissionId("create"));
            jdbc.update("INSERT INTO t_role_permission (role_id, permission, scope) VALUES (?, ?, NULL)",
                    rootRoleId(), permissionId("delete"));

            jdbc.execute(sql);
            jdbc.execute(sql);

            assertThat(rolePermissionScopes("R1"))
                    .as("set scopes are kept").containsEntry(key("list"), "own")
                    .as("a non-root row without a scope gets department").containsEntry(key("create"), "department");
            assertThat(jdbc.queryForObject("SELECT scope FROM t_role_permission WHERE role_id = ? AND permission = ?",
                    String.class, rootRoleId(), permissionId("delete")))
                    .as("a root row without a scope gets all").isEqualTo("all");
            assertThat(jdbc.queryForObject("""
                    SELECT is_nullable || ':' || column_default FROM information_schema.columns
                     WHERE table_name = 't_role_permission' AND column_name = 'scope'""", String.class))
                    .startsWith("NO:'department'");
        } finally {
            jdbc.update("DELETE FROM t_role_permission WHERE role_id = ? AND permission = ?",
                    rootRoleId(), permissionId("delete"));
            jdbc.update("UPDATE t_role_permission SET scope = 'department' WHERE scope IS NULL");
            jdbc.execute("ALTER TABLE t_role_permission ALTER COLUMN scope SET NOT NULL");
        }
    }

    private int rootRoleId() {
        return jdbc.queryForObject("""
                SELECT r.id FROM t_role r JOIN t_department d ON d.id = r.department
                 WHERE r.code = 'DEPT_IGRP.superadmin' AND d.code = 'DEPT_IGRP'""", Integer.class);
    }

    private int roleId(String name) {
        return jdbc.queryForObject("SELECT id FROM t_role WHERE code = ?", Integer.class, role(name));
    }

    private int permissionId(String action) {
        return jdbc.queryForObject("SELECT id FROM t_permission WHERE permission_key = ?", Integer.class, key(action));
    }

    // ---- fixtures ----

    private HttpResponse<String> setScopes(String roleName, String json) {
        return api.putJson("/api/departments/" + dept() + "/roles/" + role(roleName) + "/permissions/scopes", admin,
                json);
    }

    private Map<String, String> rolePermissionScopes(String roleName) {
        HttpResponse<String> response = api.get(
                "/api/departments/" + dept() + "/roles/" + role(roleName) + "/permissions", admin);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Object item : CompatSupport.jsonArray(response.body())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> permission = (Map<String, Object>) item;
            byKey.put((String) permission.get("key"), (String) permission.get("scope"));
        }
        return byKey;
    }

    private List<String> auditedChange(String permissionKey) {
        return jdbc.query("""
                SELECT previous_value, new_value FROM t_security_audit_log
                 WHERE entity_name = ? AND related_entity = ? AND settings_operation = 'EDIT'""",
                (rs, i) -> List.of(rs.getString(1), rs.getString(2)), permissionKey, role("R1"))
                .stream().flatMap(List::stream).toList();
    }

    private Map<String, String> fetchScopes() {
        return scopes(fetch(api.userToken(userId, email)));
    }

    private Map<String, Object> fetch(String bearer) {
        HttpResponse<String> response = api.get(FETCH + app, bearer);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> scopes(Map<String, Object> body) {
        return (Map<String, String>) body.get("scopes");
    }

    private void createRole(String name) {
        ok(api.postJson("/api/departments/" + dept() + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role(name)) + ",\"name\":\"Role " + name + "\",\"status\":\"ACTIVE\"}"));
    }

    private void grant(String roleName, String... actions) {
        StringBuilder keys = new StringBuilder();
        for (String a : actions) {
            keys.append(keys.isEmpty() ? "" : ",").append(CompatSupport.q(key(a)));
        }
        ok(api.postJson("/api/departments/" + dept() + "/roles/" + role(roleName) + "/permissions", admin,
                "[" + keys + "]"));
    }

    private String key(String action) {
        return service + ".faturas." + action;
    }

    private String dept() {
        return "SC_D_" + suffix;
    }

    private String role(String name) {
        return "SC_" + name + "_" + suffix;
    }

    private String setKey(String sub) {
        return "permissions:user:" + sub + ":app:" + app;
    }

    private static String route(String method, String path, String key) {
        return "{\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"key\":\"" + key + "\",\"annotated\":false}";
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
