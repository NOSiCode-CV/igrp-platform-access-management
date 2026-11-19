package cv.igrp.platform.access_management.compat;

import cv.igrp.framework.auth.core.route.PermissionKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #25: AM's own data visibility is the starter's scope filter. An administrator whose role is
 * in department P, with a child C and an unrelated department S:
 * <ul>
 *   <li>{@code department} (the default): sees P and C, their applications, roles and users, and
 *       users with no role; nothing of S, in lists or by id;</li>
 *   <li>{@code all}: sees S too;</li>
 *   <li>{@code own}: AM's rows have no owner, so nothing.</li>
 * </ul>
 */
class AmScopesIT extends AbstractCompatIT {

    private static final String SERVICE = "access-management-api";
    private static final String APP = "APP_IGRP_CENTER";

    /** The GET routes the administrator is given, as path patterns. */
    private static final List<String> ROUTES = List.of(
            "/api/departments", "/api/departments/{id}", "/api/departments/{code}/roles",
            "/api/applications", "/api/applications/{id}",
            "/api/roles/{id}", "/api/users", "/api/users/{id}");

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String root;
    private String suffix;
    private String admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        api = new CompatSupport(context);
        root = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        department("P", null);
        department("C", dept("P"));
        department("S", null);
        createRole("P", "ADMIN");
        createRole("C", "STAFF");
        createRole("S", "STAFF");
        application("C", "P");
        application("S");

        admin = user("admin");
        assign(admin, "P", "ADMIN");
        assign(user("child"), "C", "STAFF");
        assign(user("sibling"), "S", "STAFF");
        user("new");

        ROUTES.forEach(route -> grant(PermissionKeys.derive(SERVICE, "GET", route)));
        adminToken = api.userToken(admin, email("admin"));
    }

    @AfterEach
    void retire() {
        // Shared database: other tests' baselines list the active departments.
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "SCD\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("department: lists show the administrator's subtree and nothing of the other department")
    void departmentLists() {
        assertThat(body("/api/departments?name=" + suffix)).contains(dept("P"), dept("C")).doesNotContain(dept("S"));
        assertThat(body("/api/applications?name=" + suffix)).contains(app("C")).doesNotContain(app("S"));
        assertThat(body("/api/departments/" + dept("C") + "/roles")).contains(role("C", "STAFF"));
        assertThat(body("/api/departments/" + dept("S") + "/roles")).doesNotContain(role("S", "STAFF"));
        assertThat(body("/api/users?email=" + suffix.toLowerCase()))
                .contains(email("admin"), email("child"), email("new"))
                .doesNotContain(email("sibling"));
    }

    @Test
    @DisplayName("department: a read by id outside the subtree finds nothing (404); inside, it is read")
    void departmentById() {
        assertThat(status("/api/departments/" + id("t_department", dept("C")))).isEqualTo(200);
        assertThat(status("/api/departments/" + id("t_department", dept("S")))).isEqualTo(404);
        assertThat(status("/api/applications/" + id("t_application", app("C")))).isEqualTo(200);
        assertThat(status("/api/applications/" + id("t_application", app("S")))).isEqualTo(404);
        assertThat(status("/api/roles/" + id("t_role", role("C", "STAFF")) + "?departmentCode=" + dept("C"))).isEqualTo(200);
        assertThat(status("/api/roles/" + id("t_role", role("S", "STAFF")) + "?departmentCode=" + dept("S"))).isEqualTo(404);
        assertThat(status("/api/users/" + userId("child"))).isEqualTo(200);
        assertThat(status("/api/users/" + userId("new"))).isEqualTo(200);
        assertThat(status("/api/users/" + userId("sibling"))).isEqualTo(404);
    }

    @Test
    @DisplayName("all: the administrator sees every row")
    void all() {
        rescope("all");

        assertThat(body("/api/departments?name=" + suffix)).contains(dept("P"), dept("C"), dept("S"));
        assertThat(body("/api/applications?name=" + suffix)).contains(app("C"), app("S"));
        assertThat(body("/api/users?email=" + suffix.toLowerCase())).contains(email("sibling"));
        assertThat(status("/api/roles/" + id("t_role", role("S", "STAFF")) + "?departmentCode=" + dept("S"))).isEqualTo(200);
    }

    @Test
    @DisplayName("own: AM's rows have no owner, so the administrator sees none of them")
    void own() {
        rescope("own");

        assertThat(body("/api/departments?name=" + suffix)).doesNotContain(dept("P"), dept("C"), dept("S"));
        assertThat(body("/api/applications?name=" + suffix)).doesNotContain(app("C"), app("S"));
        assertThat(status("/api/departments/" + id("t_department", dept("P")))).isEqualTo(404);
        assertThat(status("/api/users/" + userId("child"))).isEqualTo(404);
    }

    // ---- data ----

    private void department(String name, String parentCode) {
        ok(api.postJson("/api/departments", root, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Scope " + name + " " + suffix + "\",\"status\":\"ACTIVE\""
                + (parentCode == null ? "" : ",\"parentCode\":" + CompatSupport.q(parentCode)) + "}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", root, "[" + CompatSupport.q(APP) + "]"));
    }

    private void createRole(String department, String name) {
        ok(api.postJson("/api/departments/" + dept(department) + "/roles", root,
                "{\"code\":" + CompatSupport.q(role(department, name)) + ",\"name\":\"Role " + name
                        + "\",\"status\":\"ACTIVE\"}"));
    }

    /** An application linked to the department and, first, to its parents, as AM requires. */
    private void application(String department, String... parents) {
        api.createApplication(app(department));
        for (String linked : concat(parents, department)) {
            ok(api.postJson("/api/departments/" + dept(linked) + "/applications", root,
                    "[" + CompatSupport.q(app(department)) + "]"));
        }
    }

    private static String[] concat(String[] first, String last) {
        String[] all = java.util.Arrays.copyOf(first, first.length + 1);
        all[first.length] = last;
        return all;
    }

    private String user(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Scope User', 'ACTIVE')""", id, email(name), email(name));
        return id;
    }

    private void assign(String userId, String department, String roleName) {
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", root,
                "{\"roles\":[" + CompatSupport.q(role(department, roleName)) + "]}"));
    }

    private void grant(String permissionKey) {
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope)
                SELECT r.id, p.id, 'department' FROM t_role r, t_permission p
                 WHERE r.code = ? AND p.permission_key = ? AND p.status = 'ACTIVE'""",
                role("P", "ADMIN"), permissionKey);
    }

    /** Changes the administrator's scope on every granted key, past the API: drop the cached set by hand. */
    private void rescope(String scope) {
        jdbc.update("UPDATE t_role_permission SET scope = ? WHERE role_id = (SELECT id FROM t_role WHERE code = ?)",
                scope, role("P", "ADMIN"));
        context.getBean(StringRedisTemplate.class).delete("permissions:user:" + admin + ":app:" + APP);
    }

    // ---- reads ----

    private String body(String path) {
        HttpResponse<String> response = api.get(path, adminToken);
        assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(200);
        return response.body();
    }

    private int status(String path) {
        return api.get(path, adminToken).statusCode();
    }

    private Integer id(String table, String code) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE code = ?", Integer.class, code);
    }

    private String userId(String name) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE email = ?", String.class, email(name));
    }

    private String dept(String name) {
        return "SCD_" + name + "_" + suffix;
    }

    private String role(String department, String name) {
        return "SCR_" + department + "_" + name + "_" + suffix;
    }

    private String app(String department) {
        return "SCA_" + department + "_" + suffix;
    }

    private String email(String name) {
        return "scope-" + name + "-" + suffix.toLowerCase() + "@example.cv";
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
