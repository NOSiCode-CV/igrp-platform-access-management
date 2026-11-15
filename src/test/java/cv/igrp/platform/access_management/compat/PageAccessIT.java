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
 * v3-26 (#34): a frontend page is never rendered unless AM allows it. The
 * pages are stored as #35's manifest registration stores them.
 */
class PageAccessIT extends AbstractCompatIT {

    private static final String CHECK = "/api/authorize/page";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String application;
    private String userId;

    @BeforeEach
    void applicationWithKeysAndPages() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "pa-" + suffix.toLowerCase();
        application = "PA_APP_" + suffix;
        int applicationId = api.createApplication(application);
        String serviceToken = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
        ok(api.postJson("/api/routes/register", serviceToken, "{\"service\":" + CompatSupport.q(service)
                + ",\"version\":\"1.0.0\",\"routes\":[" + route("faturas.list") + "," + route("faturas.read") + "]}"));
        page(applicationId, "faturas", "/faturas", List.of(key("faturas.list")), List.of());
        page(applicationId, "fatura", "/faturas/[id]", List.of(key("faturas.list"), key("faturas.read")),
                List.of(key("faturas.margem.read")));

        userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Page User', 'ACTIVE')""", userId, email(), email());
    }

    @AfterEach
    void retireDepartments() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "PAD\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("Without a role valid for the application, no page is allowed")
    void applicationDenied() {
        assertThat(check("/faturas")).containsEntry("allowed", false).containsEntry("reason", "NO_APPLICATION_ACCESS");
    }

    @Test
    @DisplayName("With the application but not the page's required keys: denied, naming the missing ones")
    void pagePermissionMissing() {
        department("D1", "R1");
        grant("R1", "faturas.list");
        assign("D1", "R1");

        Map<String, Object> decision = check("/faturas/42");

        assertThat(decision).containsEntry("allowed", false).containsEntry("reason", "MISSING_PERMISSIONS")
                .containsEntry("page", "app." + application + ".fatura")
                .containsEntry("missing", List.of(key("faturas.read")));
    }

    @Test
    @DisplayName("With the application and every required key: allowed; optional keys are not required")
    void bothAllowed() {
        department("D1", "R1");
        grant("R1", "faturas.list", "faturas.read");
        assign("D1", "R1");

        assertThat(check("/faturas/42")).containsEntry("allowed", true).containsEntry("reason", "ALLOWED")
                .containsEntry("page", "app." + application + ".fatura");
        assertThat(check("/faturas?page=2")).containsEntry("allowed", true)
                .containsEntry("page", "app." + application + ".faturas");
    }

    @Test
    @DisplayName("A path no registered page serves is denied, even with application access (default deny)")
    void unregisteredPage() {
        department("D1", "R1");
        grant("R1", "faturas.list", "faturas.read");
        assign("D1", "R1");

        assertThat(check("/relatorios")).containsEntry("allowed", false).containsEntry("reason", "PAGE_NOT_REGISTERED");
    }

    @Test
    @DisplayName("Several valid roles and none chosen: denied until the user picks one")
    void noActiveRole() {
        department("D1", "R1", "R2");
        grant("R1", "faturas.list");
        assign("D1", "R1", "R2");

        assertThat(check("/faturas")).containsEntry("allowed", false).containsEntry("reason", "NO_ACTIVE_ROLE");
    }

    @Test
    @DisplayName("Root is allowed a registered page only; an unknown application looks like no access; a service account gets no page")
    void rootUnknownApplicationAndServiceAccount() {
        assertThat(body(api.postJson(CHECK, admin, request(application, "/faturas/7"))))
                .containsEntry("allowed", true).containsEntry("reason", "ROOT");
        assertThat(body(api.postJson(CHECK, admin, request(application, "/anything"))))
                .containsEntry("allowed", false).containsEntry("reason", "PAGE_NOT_REGISTERED");

        assertThat(body(api.postJson(CHECK, token(), request("NO_SUCH_APP_" + suffix, "/faturas"))))
                .containsEntry("allowed", false).containsEntry("reason", "NO_APPLICATION_ACCESS");

        String serviceAccount = api.serviceToken(api.registerM2MClient(service + "-sa", null, List.of()));
        assertThat(body(api.postJson(CHECK, serviceAccount, request(application, "/faturas"))))
                .containsEntry("allowed", false).containsEntry("reason", "NOT_A_USER");
    }

    @Test
    @DisplayName("The chosen role is used; a page with no required key needs application access only; a deleted page is gone")
    void chosenRoleAndPagesWithoutKeys() {
        department("D1", "R1", "R2");
        grant("R1", "faturas.list");
        assign("D1", "R1", "R2");
        HttpResponse<String> chosen = api.putJson("/api/users/me/role/active?applicationCode=" + application, token(),
                "{\"roleCode\":" + CompatSupport.q(role("R1")) + ",\"departmentCode\":" + CompatSupport.q(dept("D1")) + "}");
        ok(chosen);
        int applicationId = jdbc.queryForObject("SELECT id FROM t_application WHERE code = ?", Integer.class, application);
        page(applicationId, "inicio", "/", List.of(), List.of());

        assertThat(check("/faturas")).containsEntry("allowed", true).containsEntry("reason", "ALLOWED");
        assertThat(check("/")).containsEntry("allowed", true).containsEntry("page", "app." + application + ".inicio");

        jdbc.update("UPDATE t_application_page SET status = 'DELETED' WHERE page_key = ?", "app." + application + ".faturas");
        assertThat(check("/faturas")).containsEntry("reason", "PAGE_NOT_REGISTERED");
    }

    @Test
    @DisplayName("A path a router could resolve elsewhere is denied; an inactive user is 401")
    void unsafePathsAndInactiveUser() {
        department("D1", "R1");
        grant("R1", "faturas.list", "faturas.read");
        assign("D1", "R1");

        assertThat(check("/faturas/../admin")).containsEntry("reason", "PAGE_NOT_REGISTERED");
        assertThat(check("/faturas/a%2Fb")).containsEntry("reason", "PAGE_NOT_REGISTERED");

        jdbc.update("UPDATE t_user SET status = 'INACTIVE' WHERE id = ?", userId);
        assertThat(api.postJson(CHECK, token(), request(application, "/faturas")).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Anonymous is 401; a missing path is 400")
    void anonymousAndInvalid() {
        assertThat(api.postJson(CHECK, null, request(application, "/faturas")).statusCode()).isEqualTo(401);
        assertThat(api.postJson(CHECK, token(), "{\"applicationCode\":" + CompatSupport.q(application) + "}")
                .statusCode()).isEqualTo(400);
    }

    private Map<String, Object> check(String path) {
        return body(api.postJson(CHECK, token(), request(application, path)));
    }

    private static String request(String applicationCode, String path) {
        return "{\"applicationCode\":" + CompatSupport.q(applicationCode) + ",\"path\":" + CompatSupport.q(path) + "}";
    }

    private Map<String, Object> body(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    /** A page as #35's registration stores it. */
    private void page(int applicationId, String name, String path, List<String> required, List<String> optional) {
        Integer pageId = jdbc.queryForObject("""
                INSERT INTO t_application_page (page_key, application_id, path, last_seen_version)
                VALUES (?, ?, ?, '1.0.0') RETURNING id""", Integer.class, "app." + application + "." + name,
                applicationId, path);
        required.forEach(k -> jdbc.update(
                "INSERT INTO t_application_page_permission (page_id, permission_key, required) VALUES (?, ?, true)",
                pageId, k));
        optional.forEach(k -> jdbc.update("""
                INSERT INTO t_application_page_permission (page_id, permission_key, required, label)
                VALUES (?, ?, false, 'Margem')""", pageId, k));
    }

    private void department(String name, String... roles) {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", admin, "[" + CompatSupport.q(application) + "]"));
        for (String r : roles) {
            ok(api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}"));
        }
    }

    private void grant(String roleName, String... keys) {
        for (String k : keys) {
            jdbc.update("""
                    INSERT INTO t_role_permission (role_id, permission, scope)
                    SELECT r.id, p.id, 'department' FROM t_role r, t_permission p
                     WHERE r.code = ? AND p.permission_key = ?""", role(roleName), key(k));
        }
    }

    private void assign(String department, String... roles) {
        StringBuilder codes = new StringBuilder();
        for (String r : roles) {
            codes.append(codes.isEmpty() ? "" : ",").append(CompatSupport.q(role(r)));
        }
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", admin,
                "{\"roles\":[" + codes + "]}"));
    }

    private String token() {
        return api.userToken(userId, email());
    }

    private String email() {
        return "pa-" + userId + "@example.cv";
    }

    private String route(String rest) {
        return "{\"method\":\"GET\",\"path\":\"/api/v1/" + rest.replace('.', '/') + "\",\"key\":"
                + CompatSupport.q(key(rest)) + ",\"annotated\":true}";
    }

    private String key(String rest) {
        return service + "." + rest;
    }

    private String dept(String name) {
        return "PAD_" + name + "_" + suffix;
    }

    private String role(String name) {
        return "PAR_" + name + "_" + suffix;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
