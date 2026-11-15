package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.routes.domain.service.PendingKeyDeletions;
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
 * v3-32 (#40): the {@code @FieldPermission} keys the 0.3 starter sends as
 * {@code fields} in the route registration are registered like route keys:
 * created, granted, version-gated and renamed with them.
 */
class FieldKeyRegistrationIT extends AbstractCompatIT {

    private static final String REGISTER = "/api/routes/register";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PendingKeyDeletions pendingDeletions;

    private CompatSupport api;
    private String admin;
    private String service;
    private String application;
    private String token;
    private String suffix;

    @BeforeEach
    void serviceWithItsOwnClient() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        service = "fk-" + suffix;
        application = "FK_APP_" + suffix.toUpperCase();
        int applicationId = api.createApplication(application);
        token = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
    }

    @AfterEach
    void retireDepartments() {
        // Shared database: root's department list is part of another test's contract baseline.
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", "FK_D_" + suffix.toUpperCase());
    }

    @Test
    @DisplayName("Field keys are created with their name and description, counted, and granted like route keys")
    void fieldKeysAreCreatedAndGranted() {
        HttpResponse<String> response = register("1.0.0", List.of("faturas.list"),
                "{\"key\":" + CompatSupport.q(key("faturas.margem.read")) + ",\"name\":\"Margem " + suffix + "\","
                        + "\"description\":\"A fatura's margin\"},"
                        + "{\"key\":" + CompatSupport.q(key("faturas.custo.read")) + "}");

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("created", 3);
        Map<String, Object> margem = jdbc.queryForMap("""
                SELECT p.name, p.description, p.status, p.service, a.code AS app FROM t_permission p
                  JOIN t_application a ON a.id = p.application_id WHERE p.permission_key = ?""", key("faturas.margem.read"));
        assertThat(margem).containsEntry("name", "Margem " + suffix).containsEntry("description", "A fatura's margin")
                .containsEntry("status", "ACTIVE").containsEntry("service", service).containsEntry("app", application);
        assertThat(jdbc.queryForObject("SELECT name FROM t_permission WHERE permission_key = ?", String.class,
                key("faturas.custo.read"))).as("no name: the key").isEqualTo(key("faturas.custo.read"));

        // The grant endpoints take the permission's name.
        String userId = userGranted("Margem " + suffix);
        HttpResponse<String> fetched = api.get("/api/users/me/permissions?applicationCode=" + application,
                api.userToken(userId, userId + "@example.cv"));
        assertThat(fetched.statusCode()).as(fetched.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) CompatSupport.jsonObject(fetched.body()).get("keys");
        assertThat(keys).contains(key("faturas.margem.read"));
    }

    @Test
    @DisplayName("A newer version without a field key schedules it, and it is deleted when due; a declared one is not")
    void fieldKeysFollowTheVersionGate() {
        ok(register("1.0.0", List.of("faturas.list"), field("faturas.margem.read"), field("faturas.custo.read")));

        HttpResponse<String> response = register("2.0.0", List.of("faturas.list"), field("faturas.margem.read"));

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("deleted", 1).containsEntry("created", 0);
        assertThat(pending()).containsExactly(key("faturas.custo.read"));
        jdbc.update("""
                UPDATE t_permission_pending_deletion SET due_at = now() - interval '1 second'
                 WHERE permission_id IN (SELECT id FROM t_permission WHERE service = ?)""", service);
        pendingDeletions.deleteDue();
        assertThat(status("faturas.custo.read")).isEqualTo("DELETED");
        assertThat(status("faturas.margem.read")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A registration without fields (a starter older than #23) works; its old field keys follow the gate")
    void registrationWithoutFields() {
        ok(register("1.0.0", List.of("faturas.list"), field("faturas.margem.read")));

        HttpResponse<String> response = api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"2.0.0\",\"routes\":[" + route("faturas.list") + "]}");

        ok(response);
        assertThat(pending()).containsExactly(key("faturas.margem.read"));
    }

    @Test
    @DisplayName("A key declared both as a route and as a field is one permission; as a field alone it stays declared")
    void routeAndFieldShareAKey() {
        String shared = key("faturas.read");
        HttpResponse<String> response = api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.0.0\",\"routes\":[{\"method\":\"GET\",\"path\":\"/faturas/{id}\",\"key\":"
                + CompatSupport.q(shared) + ",\"annotated\":true,\"name\":\"Ver fatura\"}],"
                + "\"fields\":[{\"key\":" + CompatSupport.q(shared) + ",\"name\":\"Field name\"}]}");

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("created", 1);
        assertThat(jdbc.queryForList("SELECT name FROM t_permission WHERE permission_key = ?", String.class, shared))
                .containsExactly("Ver fatura");

        // The route goes away in 2.0.0 but the key is still a field: declared, so not scheduled.
        HttpResponse<String> next = register("2.0.0", List.of("faturas.list"), field("faturas.read"));

        ok(next);
        assertThat(pending()).isEmpty();
        assertThat(status("faturas.read")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("An older version cancels a field key's pending deletion; a deleted field key declared again comes back")
    void olderVersionAndRollbackKeepFieldKeys() {
        ok(register("1.0.0", List.of("faturas.list"), field("faturas.margem.read"), field("faturas.custo.read")));
        ok(register("2.0.0", List.of("faturas.list")));
        assertThat(pending()).containsExactly(key("faturas.custo.read"), key("faturas.margem.read"));

        ok(register("1.5.0", List.of("faturas.list"), field("faturas.margem.read")));
        assertThat(pending()).containsExactly(key("faturas.custo.read"));

        jdbc.update("""
                UPDATE t_permission_pending_deletion SET due_at = now() - interval '1 second'
                 WHERE permission_id IN (SELECT id FROM t_permission WHERE service = ?)""", service);
        pendingDeletions.deleteDue();
        assertThat(status("faturas.custo.read")).isEqualTo("DELETED");

        HttpResponse<String> back = register("2.1.0", List.of("faturas.list"), field("faturas.custo.read"));
        ok(back);
        assertThat(CompatSupport.jsonObject(back.body())).containsEntry("created", 1);
        assertThat(status("faturas.custo.read")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A field key outside the service's namespace or with an unknown action is refused, and nothing is created")
    void invalidFieldKeysAreRefused() {
        HttpResponse<String> foreign = register("1.0.0", List.of("faturas.list"),
                "{\"key\":\"other-api.faturas.margem.read\"}");
        HttpResponse<String> badAction = register("1.0.0", List.of("faturas.list"),
                "{\"key\":" + CompatSupport.q(service + ".faturas.margem.approve") + "}");

        assertThat(foreign.statusCode()).as(foreign.body()).isEqualTo(400);
        assertThat(badAction.statusCode()).as(badAction.body()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_permission WHERE service = ?", Integer.class, service))
                .isZero();
    }

    @Test
    @DisplayName("A rename by client_id rewrites the field keys with the route keys")
    void renameCarriesFieldKeys() {
        ok(register("1.0.0", List.of("faturas.list"), field("faturas.margem.read")));
        String renamed = service + "-v2";

        HttpResponse<String> response = api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(renamed)
                + ",\"version\":\"2.0.0\",\"routes\":[" + route(renamed + ".faturas.list", true) + "],"
                + "\"fields\":[{\"key\":" + CompatSupport.q(renamed + ".faturas.margem.read") + "}]}");

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("renamed", 2).containsEntry("created", 0);
        assertThat(jdbc.queryForObject("SELECT status FROM t_permission WHERE permission_key = ?", String.class,
                renamed + ".faturas.margem.read")).isEqualTo("ACTIVE");
    }

    private HttpResponse<String> register(String version, List<String> routeKeys, String... fields) {
        StringBuilder routes = new StringBuilder();
        for (String rest : routeKeys) {
            routes.append(routes.isEmpty() ? "" : ",").append(route(rest));
        }
        return api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":" + CompatSupport.q(version) + ",\"routes\":[" + routes + "],"
                + "\"fields\":[" + String.join(",", fields) + "]}");
    }

    private String route(String rest) {
        return route(key(rest), true);
    }

    private static String route(String fullKey, boolean annotated) {
        return "{\"method\":\"GET\",\"path\":\"/api/v1/faturas\",\"key\":" + CompatSupport.q(fullKey)
                + ",\"annotated\":" + annotated + "}";
    }

    private String field(String rest) {
        return "{\"key\":" + CompatSupport.q(key(rest)) + "}";
    }

    /** A user whose role, in a department of the application, is granted the permission (by name). */
    private String userGranted(String permissionName) {
        String department = "FK_D_" + suffix.toUpperCase();
        String role = "FK_R_" + suffix.toUpperCase();
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(department)
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + department + "/applications", admin, "[" + CompatSupport.q(application) + "]"));
        ok(api.postJson("/api/departments/" + department + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role) + ",\"name\":\"Role\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + department + "/permissions", admin, "[" + CompatSupport.q(permissionName) + "]"));
        ok(api.postJson("/api/departments/" + department + "/roles/" + role + "/permissions", admin,
                "[" + CompatSupport.q(permissionName) + "]"));
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Field User', 'ACTIVE')""", id, id + "@example.cv", id + "@example.cv");
        ok(api.postJson("/api/users/" + id + "/departments/" + department + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role) + "]}"));
        return id;
    }

    private List<String> pending() {
        return jdbc.queryForList("""
                SELECT p.permission_key FROM t_permission_pending_deletion pd JOIN t_permission p ON p.id = pd.permission_id
                 WHERE p.service = ? ORDER BY 1""", String.class, service);
    }

    private String status(String rest) {
        return jdbc.queryForObject("SELECT status FROM t_permission WHERE permission_key = ?", String.class, key(rest));
    }

    private String key(String rest) {
        return service + "." + rest;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
