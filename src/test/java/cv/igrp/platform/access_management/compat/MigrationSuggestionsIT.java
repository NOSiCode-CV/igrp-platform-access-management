package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-29 (#37): business permissions are never converted automatically. A
 * registration whose routes name their legacy permission ({@code legacyKey})
 * produces suggestions; an administrator confirms one, which copies the legacy
 * permission's roles, departments and scopes to the route key, or dismisses it.
 */
class MigrationSuggestionsIT extends AbstractCompatIT {

    private static final String SUGGESTIONS = "/api/migration/suggestions";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String application;
    private String token;
    private String legacy;
    private String department;

    @BeforeEach
    void legacyPermissionWithHolders() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "ms-" + suffix.toLowerCase();
        application = "MS_APP_" + suffix;
        int applicationId = api.createApplication(application);
        token = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));

        legacy = "igrp.faturas.view." + suffix.toLowerCase();
        department = "MSD_" + suffix;
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(department)
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + department + "/applications", admin, "[" + CompatSupport.q(application) + "]"));
        for (String r : List.of("A", "B")) {
            ok(api.postJson("/api/departments/" + department + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}"));
        }
        Integer legacyId = jdbc.queryForObject("""
                INSERT INTO t_permission (name, description, status, created_by, created_date)
                VALUES (?, 'Ver faturas (0.2)', 'ACTIVE', 'compat', now()) RETURNING id""", Integer.class, legacy);
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope) SELECT id, ?, 'own' FROM t_role WHERE code = ?""",
                legacyId, role("A"));
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope) SELECT id, ?, 'all' FROM t_role WHERE code = ?""",
                legacyId, role("B"));
        jdbc.update("""
                INSERT INTO t_permission_department (permission_id, department) SELECT ?, id FROM t_department WHERE code = ?""",
                legacyId, department);
    }

    @AfterEach
    void retireDepartment() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", department);
    }

    @Test
    @DisplayName("A registration with legacyKey produces a suggestion; nothing is copied without confirmation")
    void suggestionWithoutCopy() {
        ok(register("1.0.0", legacy));

        Map<String, Object> suggestion = pendingSuggestion();

        assertThat(suggestion).containsEntry("key", key("faturas.list")).containsEntry("legacyName", legacy)
                .containsEntry("status", "PENDING").containsEntry("service", service)
                .containsEntry("applicationCode", application);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) suggestion.get("roles");
        assertThat(roles).extracting(r -> r.get("roleCode") + "=" + r.get("scope"))
                .containsExactlyInAnyOrder(role("A") + "=own", role("B") + "=all");
        assertThat(suggestion).containsEntry("departments", List.of(department));
        assertThat(assignments(key("faturas.list"))).isZero();

        // A later registration does not duplicate it.
        ok(register("1.1.0", legacy));
        assertThat(suggestions()).hasSize(1);
    }

    @Test
    @DisplayName("Confirming copies the roles with their scopes and the departments to the route key, and audits it")
    void confirmCopies() {
        ok(register("1.0.0", legacy));
        Object id = pendingSuggestion().get("id");

        HttpResponse<String> confirmed = api.postJson(SUGGESTIONS + "/" + id + "/confirm", admin, "{}");

        ok(confirmed);
        assertThat(CompatSupport.jsonObject(confirmed.body())).containsEntry("status", "CONFIRMED");
        assertThat(jdbc.queryForList("""
                SELECT r.code || '=' || rp.scope FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission WHERE p.permission_key = ?""", String.class,
                key("faturas.list"))).containsExactlyInAnyOrder(role("A") + "=own", role("B") + "=all");
        assertThat(jdbc.queryForList("""
                SELECT d.code FROM t_permission_department pd JOIN t_department d ON d.id = pd.department
                  JOIN t_permission p ON p.id = pd.permission_id WHERE p.permission_key = ?""", String.class,
                key("faturas.list"))).containsExactly(department);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log WHERE entity_name = ? AND settings_operation = 'ASSOCIATE'""",
                Long.class, key("faturas.list"))).isOne();
        // The legacy permission keeps its assignments: services on 0.2 still use it.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_permission rp JOIN t_permission p ON p.id = rp.permission WHERE p.name = ?",
                Integer.class, legacy)).isEqualTo(2);

        assertThat(api.postJson(SUGGESTIONS + "/" + id + "/confirm", admin, "{}").statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("Dismissing copies nothing and leaves the list; an unknown suggestion is 404")
    void dismiss() {
        ok(register("1.0.0", legacy));
        Object id = pendingSuggestion().get("id");

        ok(api.postJson(SUGGESTIONS + "/" + id + "/dismiss", admin, "{}"));

        assertThat(suggestions()).isEmpty();
        assertThat(assignments(key("faturas.list"))).isZero();
        assertThat(api.postJson(SUGGESTIONS + "/999999999/confirm", admin, "{}").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("No suggestion for an unknown legacy key, or one nobody holds")
    void noSuggestionWithoutHolders() {
        jdbc.update("INSERT INTO t_permission (name, status, created_by, created_date) VALUES (?, 'ACTIVE', 'compat', now())",
                legacy + ".unheld");
        ok(api.postJson("/api/routes/register", token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.0.0\",\"routes\":[" + route("faturas.list", "igrp.no.such." + suffix) + ","
                + route("faturas.read", legacy + ".unheld") + "]}"));

        assertThat(suggestions()).isEmpty();
    }

    @Test
    @DisplayName("A role that already holds the key keeps its scope; the suggestion shows it")
    void existingScopeIsKept() {
        ok(register("1.0.0", legacy));
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope)
                SELECT r.id, p.id, 'department' FROM t_role r, t_permission p WHERE r.code = ? AND p.permission_key = ?""",
                role("A"), key("faturas.list"));

        Map<String, Object> suggestion = pendingSuggestion();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) suggestion.get("roles");
        assertThat(roles).extracting(r -> r.get("roleCode") + "=" + r.get("scope") + "/" + r.get("currentScope"))
                .containsExactlyInAnyOrder(role("A") + "=own/department", role("B") + "=all/null");

        ok(api.postJson(SUGGESTIONS + "/" + suggestion.get("id") + "/confirm", admin, "{}"));

        assertThat(jdbc.queryForList("""
                SELECT r.code || '=' || rp.scope FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission WHERE p.permission_key = ?""", String.class,
                key("faturas.list"))).containsExactlyInAnyOrder(role("A") + "=department", role("B") + "=all");
    }

    @Test
    @DisplayName("A legacy permission held only in another application's departments is never suggested")
    void otherApplicationsHoldersAreNotSuggested() {
        String other = "MSO_" + suffix;
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(other)
                + ",\"name\":\"Other\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + other + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role("O")) + ",\"name\":\"Other role\",\"status\":\"ACTIVE\"}"));
        String foreign = legacy + ".foreign";
        Integer foreignId = jdbc.queryForObject("""
                INSERT INTO t_permission (name, status, created_by, created_date)
                VALUES (?, 'ACTIVE', 'compat', now()) RETURNING id""", Integer.class, foreign);
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope) SELECT id, ?, 'all' FROM t_role WHERE code = ?""",
                foreignId, role("O"));

        ok(register("1.0.0", foreign));

        assertThat(suggestions()).isEmpty();
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", other);
    }

    @Test
    @DisplayName("The suggestion routes are closed to a user without their keys")
    void closedWithoutKeys() {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Suggestions User', 'ACTIVE')""", userId, userId + "@example.cv",
                userId + "@example.cv");
        String user = api.userToken(userId, userId + "@example.cv");

        assertThat(api.get(SUGGESTIONS, user).statusCode()).isEqualTo(403);
        assertThat(api.postJson(SUGGESTIONS + "/1/confirm", user, "{}").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> register(String version, String legacyKey) {
        return api.postJson("/api/routes/register", token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":" + CompatSupport.q(version) + ",\"routes\":[" + route("faturas.list", legacyKey) + "]}");
    }

    private String route(String rest, String legacyKey) {
        return "{\"method\":\"GET\",\"path\":\"/api/v1/" + rest.replace('.', '/') + "\",\"key\":"
                + CompatSupport.q(key(rest)) + ",\"annotated\":true,\"legacyKey\":" + CompatSupport.q(legacyKey) + "}";
    }

    private List<Object> suggestions() {
        HttpResponse<String> response = api.get(SUGGESTIONS + "?applicationCode=" + application, admin);
        ok(response);
        return CompatSupport.jsonArray(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingSuggestion() {
        List<Object> all = suggestions();
        assertThat(all).hasSize(1);
        return (Map<String, Object>) all.getFirst();
    }

    private long assignments(String permissionKey) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM t_role_permission rp WHERE rp.permission = p.id)
                     + (SELECT count(*) FROM t_permission_department pd WHERE pd.permission_id = p.id)
                  FROM t_permission p WHERE p.permission_key = ?""", Long.class, permissionKey);
    }

    private String key(String rest) {
        return service + "." + rest;
    }

    private String role(String name) {
        return "MSR_" + name + "_" + suffix;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
