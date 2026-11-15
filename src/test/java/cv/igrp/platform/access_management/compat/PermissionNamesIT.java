package cv.igrp.platform.access_management.compat;

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
 * v3-04 (#12): administrators manage permissions by display names they can
 * edit, by the immutable key, and deploys never undo the edits.
 */
class PermissionNamesIT extends AbstractCompatIT {

    private static final String REGISTER = "/api/routes/register";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String service;
    private String application;
    private String token;
    private String listName;

    @BeforeEach
    void serviceWithRegisteredKeys() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        service = "pn-" + suffix;
        application = "PN_APP_" + suffix.toUpperCase();
        int applicationId = api.createApplication(application);
        token = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
        listName = "Listar faturas " + suffix;
        ok(register("1.0.0"));
    }

    @Test
    @DisplayName("The list shows each key of an application by display name, the key standing in when code gave none")
    void listByDisplayName() {
        HttpResponse<String> response = api.get("/api/permissions?applicationCode=" + application, admin);

        ok(response);
        List<Object> permissions = CompatSupport.jsonArray(response.body());
        assertThat(permissions).hasSize(2);
        assertThat(permissions).extracting(p -> String.valueOf(((Map<?, ?>) p).get("name")))
                .containsExactly(listName, key("faturas.read"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) permissions.get(0);
        assertThat(first).containsEntry("key", key("faturas.list"))
                .containsEntry("service", service).containsEntry("applicationCode", application);
    }

    @Test
    @DisplayName("Name and description are updated by key, audited, and kept by a later registration")
    void updateByKeySurvivesRegistration() {
        HttpResponse<String> updated = api.putJson("/api/permissions/" + key("faturas.list"), admin,
                "{\"name\":\"Ver todas as faturas " + service + "\",\"description\":\"Lista as faturas do departamento\"}");

        ok(updated);
        assertThat(CompatSupport.jsonObject(updated.body())).containsEntry("name", "Ver todas as faturas " + service)
                .containsEntry("description", "Lista as faturas do departamento");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE entity_name = ? AND settings_operation = 'EDIT'""", Long.class, key("faturas.list"))).isOne();

        ok(register("2.0.0"));

        assertThat(jdbc.queryForMap("SELECT name, description FROM t_permission WHERE permission_key = ?",
                key("faturas.list")))
                .containsEntry("name", "Ver todas as faturas " + service)
                .containsEntry("description", "Lista as faturas do departamento");
    }

    @Test
    @DisplayName("An unknown key is 404, a blank or reserved name 400, a name another permission has 409")
    void refusals() {
        assertThat(api.putJson("/api/permissions/" + key("nope.read"), admin, "{\"name\":\"X\"}").statusCode())
                .isEqualTo(404);
        assertThat(api.putJson("/api/permissions/" + key("faturas.list"), admin, "{\"name\":\" \"}").statusCode())
                .isEqualTo(400);
        assertThat(api.putJson("/api/permissions/" + key("faturas.list"), admin, "{\"name\":\"igrp.users.view\"}")
                .statusCode()).isEqualTo(400);
        HttpResponse<String> taken = api.putJson("/api/permissions/" + key("faturas.read"), admin,
                "{\"name\":" + CompatSupport.q(listName) + "}");
        assertThat(taken.statusCode()).as(taken.body()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT name FROM t_permission WHERE permission_key = ?", String.class,
                key("faturas.read"))).isEqualTo(key("faturas.read"));
    }

    @Test
    @DisplayName("Names and descriptions are trimmed, a blank description is cleared, and a no-op edit is not audited")
    void trimmingAndNoOp() {
        String name = "  Ver fatura " + service + "  ";
        HttpResponse<String> updated = api.putJson("/api/permissions/" + key("faturas.read"), admin,
                "{\"name\":" + CompatSupport.q(name) + ",\"description\":\"   \"}");

        ok(updated);
        assertThat(CompatSupport.jsonObject(updated.body())).containsEntry("name", name.trim())
                .containsEntry("description", null);
        ok(api.putJson("/api/permissions/" + key("faturas.read"), admin,
                "{\"name\":" + CompatSupport.q(name.trim()) + "}"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE entity_name = ? AND settings_operation = 'EDIT'""", Long.class, key("faturas.read"))).isOne();
    }

    @Test
    @DisplayName("A newly registered key whose declared name is taken gets its key as its name")
    void registrationNeverDuplicatesAName() {
        HttpResponse<String> response = api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.1.0\",\"routes\":[{\"method\":\"POST\",\"path\":\"/api/v1/faturas\","
                + "\"key\":" + CompatSupport.q(key("faturas.create")) + ",\"annotated\":true,\"name\":"
                + CompatSupport.q(listName) + "}]}");

        ok(response);
        assertThat(jdbc.queryForObject("SELECT name FROM t_permission WHERE permission_key = ?", String.class,
                key("faturas.create"))).isEqualTo(key("faturas.create"));
    }

    @Test
    @DisplayName("Both routes are closed to a user without their keys")
    void closedWithoutKeys() {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Names User', 'ACTIVE')""", userId, userId + "@example.cv", userId + "@example.cv");
        String user = api.userToken(userId, userId + "@example.cv");

        assertThat(api.get("/api/permissions?applicationCode=" + application, user).statusCode()).isEqualTo(403);
        assertThat(api.putJson("/api/permissions/" + key("faturas.list"), user, "{\"name\":\"X\"}").statusCode())
                .isEqualTo(403);
    }

    private HttpResponse<String> register(String version) {
        return api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":" + CompatSupport.q(version) + ",\"routes\":["
                + "{\"method\":\"GET\",\"path\":\"/api/v1/faturas\",\"key\":" + CompatSupport.q(key("faturas.list"))
                + ",\"annotated\":true,\"name\":" + CompatSupport.q(listName) + "},"
                + "{\"method\":\"GET\",\"path\":\"/api/v1/faturas/{id}\",\"key\":" + CompatSupport.q(key("faturas.read"))
                + ",\"annotated\":false}]}");
    }

    private String key(String rest) {
        return service + "." + rest;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
