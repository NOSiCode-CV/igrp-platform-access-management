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
 * v3-27 (#35): granting a page to a role assigns its required keys, and the
 * optional ones only when chosen; the page check then allows the page.
 */
class PageGrantIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String application;
    private String serviceToken;

    @BeforeEach
    void pagesAndDepartment() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "pg-" + suffix.toLowerCase();
        application = "PG_APP_" + suffix;
        int applicationId = api.createApplication(application);
        serviceToken = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
        ok(api.postJson("/api/routes/register", serviceToken, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.0.0\",\"routes\":[" + route("faturas.list") + "," + route("faturas.read") + ","
                + route("faturas.margem.read") + "]}"));
        ok(api.postJson("/api/manifests/register", serviceToken, "{\"version\":\"1.0.0\",\"pages\":["
                + "{\"application\":" + CompatSupport.q(application) + ",\"page\":" + CompatSupport.q(page("fatura"))
                + ",\"path\":\"/faturas/[id]\",\"required\":[" + CompatSupport.q(key("faturas.list")) + ","
                + CompatSupport.q(key("faturas.read")) + "],\"optional\":[{\"key\":"
                + CompatSupport.q(key("faturas.margem.read")) + ",\"label\":\"Margem\"}]},"
                + "{\"application\":" + CompatSupport.q(application) + ",\"page\":" + CompatSupport.q(page("inicio"))
                + ",\"path\":\"/\",\"required\":[],\"optional\":[]}]}"));

        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept())
                + ",\"name\":\"Dept PG\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept() + "/applications", admin, "[" + CompatSupport.q(application) + "]"));
        ok(api.postJson("/api/departments/" + dept() + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role()) + ",\"name\":\"Role PG\",\"status\":\"ACTIVE\"}"));
    }

    @AfterEach
    void retireDepartment() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", dept());
    }

    @Test
    @DisplayName("Granting a page assigns its required keys only; the user with the role may then open it")
    void requiredOnly() {
        available("faturas.list", "faturas.read", "faturas.margem.read");

        Map<String, Object> granted = grant("{\"page\":" + CompatSupport.q(page("fatura")) + "}", 200);

        assertThat(granted).containsEntry("granted", List.of(key("faturas.list"), key("faturas.read")));
        assertThat(held()).containsExactlyInAnyOrder(key("faturas.list"), key("faturas.read"));

        String userId = userWithRole();
        assertThat(CompatSupport.jsonObject(api.postJson("/api/authorize/page",
                api.userToken(userId, "pg-" + userId + "@example.cv"),
                "{\"applicationCode\":" + CompatSupport.q(application) + ",\"path\":\"/faturas/9\"}").body()))
                .containsEntry("allowed", true).containsEntry("page", page("fatura"));
    }

    @Test
    @DisplayName("A chosen optional key is assigned with the required ones; a key that is not the page's is refused")
    void optionalWhenChosen() {
        available("faturas.list", "faturas.read", "faturas.margem.read");

        grant("{\"page\":" + CompatSupport.q(page("fatura")) + ",\"optional\":["
                + CompatSupport.q(key("faturas.margem.read")) + "]}", 200);
        assertThat(held()).containsExactlyInAnyOrder(key("faturas.list"), key("faturas.read"),
                key("faturas.margem.read"));

        grant("{\"page\":" + CompatSupport.q(page("fatura")) + ",\"optional\":[\"other.thing.read\"]}", 400);
    }

    @Test
    @DisplayName("All or nothing: a key the department lacks refuses the grant and assigns nothing")
    void allOrNothing() {
        available("faturas.list");

        grant("{\"page\":" + CompatSupport.q(page("fatura")) + "}", 409);

        assertThat(held()).isEmpty();
    }

    @Test
    @DisplayName("An unknown page is 404; a page with no keys grants nothing")
    void unknownAndEmptyPages() {
        grant("{\"page\":\"app.NOPE.x\"}", 404);
        assertThat(grant("{\"page\":" + CompatSupport.q(page("inicio")) + "}", 200))
                .containsEntry("granted", List.of());
    }

    private Map<String, Object> grant(String body, int status) {
        HttpResponse<String> response = api.postJson(
                "/api/departments/" + dept() + "/roles/" + role() + "/pages", admin, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return status == 200 ? CompatSupport.jsonObject(response.body()) : Map.of();
    }

    /** The department may hand these keys to its roles. */
    private void available(String... keys) {
        StringBuilder names = new StringBuilder();
        for (String k : keys) {
            names.append(names.isEmpty() ? "" : ",").append(CompatSupport.q(key(k)));
        }
        ok(api.postJson("/api/departments/" + dept() + "/permissions", admin, "[" + names + "]"));
    }

    private List<String> held() {
        return jdbc.queryForList("""
                SELECT p.permission_key FROM t_role_permission rp
                  JOIN t_role r ON r.id = rp.role_id JOIN t_permission p ON p.id = rp.permission
                 WHERE r.code = ?""", String.class, role());
    }

    private String userWithRole() {
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Page Grant User', 'ACTIVE')""",
                userId, "pg-" + userId + "@example.cv", "pg-" + userId + "@example.cv");
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept() + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role()) + "]}"));
        return userId;
    }

    private String route(String rest) {
        return "{\"method\":\"GET\",\"path\":\"/api/v1/" + rest.replace('.', '/') + "\",\"key\":"
                + CompatSupport.q(key(rest)) + ",\"annotated\":true}";
    }

    private String key(String rest) {
        return service + "." + rest;
    }

    private String page(String name) {
        return "app." + application + "." + name;
    }

    private String dept() {
        return "PGD_" + suffix;
    }

    private String role() {
        return "PGR_" + suffix;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
