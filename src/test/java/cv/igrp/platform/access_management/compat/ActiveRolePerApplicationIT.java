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
 * v3-05 (#13): a user has one active role per application, remembered across
 * logins. A role is valid for an application when the user holds it and its
 * department is linked to that application.
 */
class ActiveRolePerApplicationIT extends AbstractCompatIT {

    private static final String ACTIVE = "/api/users/me/role/active?applicationCode=";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String userId;
    private String email;

    @BeforeEach
    void freshUser() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        userId = UUID.randomUUID().toString();
        email = "ar-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Active Role User', 'ACTIVE')""", userId, email, email);
    }

    /**
     * The compat suite shares one database, and creating a department also
     * lists it under the superadmin, which HttpContractBaselineIT snapshots.
     * Retire this test's departments so later tests see the shared data only.
     */
    @AfterEach
    void retireDepartments() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "AR\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("The same user has a different active role in each application")
    void differentRolesInTwoApplications() {
        String appA = app("A");
        String appB = app("B");
        department("D1", appA, "R1", "R2");
        department("D2", appB, "R3", "R4");
        assign("D1", "R1", "R2");
        assign("D2", "R3", "R4");

        assertThat(put(token(), appA, role("R2")).statusCode()).isEqualTo(200);
        assertThat(put(token(), appB, role("R4")).statusCode()).isEqualTo(200);

        assertThat(activeRoleCode(get(token(), appA))).isEqualTo(role("R2"));
        assertThat(activeRoleCode(get(token(), appB))).isEqualTo(role("R4"));
    }

    @Test
    @DisplayName("The choice survives logout and login (a new session)")
    void choiceSurvivesANewSession() {
        String appA = app("A");
        department("D1", appA, "R1", "R2");
        assign("D1", "R1", "R2");
        put(token(), appA, role("R2"));

        Map<String, Object> afterLogin = body(get(token(), appA));

        assertThat(activeRoleCode(afterLogin)).isEqualTo(role("R2"));
        assertThat(afterLogin).containsEntry("source", "STORED");
    }

    @Test
    @DisplayName("A single valid role is the default; several and no choice give no active role")
    void defaultRole() {
        String single = app("S");
        String several = app("M");
        department("D1", single, "R1");
        department("D2", several, "R2", "R3");
        assign("D1", "R1");
        assign("D2", "R2", "R3");

        Map<String, Object> one = body(get(token(), single));
        Map<String, Object> many = body(get(token(), several));

        assertThat(activeRoleCode(one)).isEqualTo(role("R1"));
        assertThat(one).containsEntry("source", "DEFAULT");
        assertThat(many.get("activeRole")).isNull();
        assertThat(many).containsEntry("source", "NONE");
        assertThat((List<?>) many.get("availableRoles")).hasSize(2);
    }

    @Test
    @DisplayName("A role not valid for the application is refused; an unknown application is 404")
    void invalidRoleIsRefused() {
        String appA = app("A");
        String appB = app("B");
        department("D1", appA, "R1");
        department("D2", appB, "R2");
        assign("D1", "R1");
        assign("D2", "R2");

        HttpResponse<String> wrongApp = put(token(), appA, role("R2"));
        HttpResponse<String> notHeld = put(token(), appA, "NOPE_" + suffix);
        HttpResponse<String> unknownApp = get(token(), "NO_SUCH_APP_" + suffix);

        assertThat(wrongApp.statusCode()).as(wrongApp.body()).isEqualTo(400);
        assertThat(notHeld.statusCode()).as(notHeld.body()).isEqualTo(400);
        assertThat(unknownApp.statusCode()).as(unknownApp.body()).isEqualTo(404);
    }

    @Test
    @DisplayName("A stored role that is no longer held falls back to the default")
    void staleChoiceFallsBack() {
        String appA = app("A");
        department("D1", appA, "R1", "R2");
        assign("D1", "R1", "R2");
        put(token(), appA, role("R2"));

        jdbc.update("DELETE FROM t_user_role_assignment WHERE user_id = ? AND role_id = "
                + "(SELECT id FROM t_role WHERE code = ?)", userId, role("R2"));

        Map<String, Object> afterRemoval = body(get(token(), appA));
        assertThat(activeRoleCode(afterRemoval)).isEqualTo(role("R1"));
        assertThat(afterRemoval).containsEntry("source", "DEFAULT");
    }

    @Test
    @DisplayName("An expired role assignment is not valid")
    void expiredAssignmentIsNotValid() {
        String appA = app("A");
        department("D1", appA, "R1", "R2");
        assign("D1", "R1", "R2");
        jdbc.update("UPDATE t_user_role_assignment SET expires_at = now() - interval '1 day' "
                + "WHERE user_id = ? AND role_id = (SELECT id FROM t_role WHERE code = ?)", userId, role("R2"));

        assertThat(activeRoleCode(get(token(), appA))).isEqualTo(role("R1"));
        assertThat(put(token(), appA, role("R2")).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("A role code held in two of the application's departments needs the department")
    void ambiguousRoleCodeNeedsTheDepartment() {
        String appA = app("A");
        department("D1", appA, "SAME");
        department("D2", appA, "SAME");
        assign("D1", "SAME");
        assign("D2", "SAME");

        HttpResponse<String> ambiguous = put(token(), appA, role("SAME"));
        HttpResponse<String> explicit = api.putJson(ACTIVE + appA, token(),
                "{\"roleCode\":" + CompatSupport.q(role("SAME")) + ",\"departmentCode\":" + CompatSupport.q(dept("D2")) + "}");

        assertThat(ambiguous.statusCode()).as(ambiguous.body()).isEqualTo(409);
        assertThat(explicit.statusCode()).as(explicit.body()).isEqualTo(200);
        assertThat(body(explicit)).extracting("activeRole.departmentCode").isEqualTo(dept("D2"));
    }

    @Test
    @DisplayName("Each switch is audited in the switch's transaction, with the previous and new role")
    void switchIsAudited() {
        String appA = app("A");
        department("D1", appA, "R1", "R2");
        assign("D1", "R1", "R2");

        put(token(), appA, role("R1"));
        put(token(), appA, role("R2"));
        put(token(), appA, role("R2"));

        List<Map<String, Object>> rows = switches(appA);
        assertThat(rows).as("a repeated PUT of the same role writes no row").hasSize(2);
        assertThat(rows.getFirst())
                .as("first switch, several valid roles and no choice: no previous role")
                .containsEntry("previous_value", null)
                .containsEntry("new_value", label("D1", "R1"));
        assertThat(rows.get(1))
                .containsEntry("entity_name", label("D1", "R2"))
                .containsEntry("previous_value", label("D1", "R1"))
                .containsEntry("new_value", label("D1", "R2"))
                .containsEntry("settings_area", "ACCESS")
                .containsEntry("settings_operation", "ASSIGN");
    }

    @Test
    @DisplayName("Storing the only (default) role records it as the previous one too")
    void storingTheDefaultRecordsItAsPrevious() {
        String appA = app("A");
        department("D1", appA, "R1");
        assign("D1", "R1");

        put(token(), appA, role("R1"));

        assertThat(switches(appA)).singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("previous_value", label("D1", "R1"))
                        .containsEntry("new_value", label("D1", "R1")));
    }

    private List<Map<String, Object>> switches(String applicationCode) {
        return jdbc.queryForList("""
                SELECT entity_name, previous_value, new_value, settings_area, settings_operation
                  FROM t_security_audit_log
                 WHERE settings_entity_type = 'ROLE' AND settings_operation = 'ASSIGN' AND related_entity = ?
                 ORDER BY sequence_number""", applicationCode);
    }

    private String label(String department, String roleName) {
        return dept(department) + "/" + role(roleName);
    }

    @Test
    @DisplayName("Root's active role is the super-admin role in every application, and cannot be switched")
    void rootIsAlwaysSuperAdmin() {
        String appA = app("A");
        department("D1", appA, "R1");

        Map<String, Object> rootView = body(get(admin, appA));
        HttpResponse<String> rootSwitch = put(admin, appA, role("R1"));

        assertThat(activeRoleCode(rootView)).isEqualTo("DEPT_IGRP.superadmin");
        assertThat(rootView).containsEntry("source", "SUPER_ADMIN");
        assertThat(rootSwitch.statusCode()).as(rootSwitch.body()).isEqualTo(400);
    }

    // ---- fixtures through the public API ----

    private String app(String name) {
        String code = "AR_" + name + "_" + suffix;
        api.createApplication(code);
        return code;
    }

    private String dept(String name) {
        return "AR_" + name + "_" + suffix;
    }

    private String role(String name) {
        return "AR_" + name + "_" + suffix;
    }

    private void department(String name, String applicationCode, String... roles) {
        expectOk(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\"}"));
        expectOk(api.postJson("/api/departments/" + dept(name) + "/applications", admin,
                "[" + CompatSupport.q(applicationCode) + "]"));
        for (String r : roles) {
            HttpResponse<String> created = api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}");
            expectOk(created);
        }
    }

    private void assign(String department, String... roles) {
        StringBuilder codes = new StringBuilder();
        for (String r : roles) {
            codes.append(codes.isEmpty() ? "" : ",").append(CompatSupport.q(role(r)));
        }
        expectOk(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", admin,
                "{\"roles\":[" + codes + "]}"));
    }

    private String token() {
        return api.userToken(userId, email);
    }

    private HttpResponse<String> get(String bearer, String applicationCode) {
        return api.get(ACTIVE + applicationCode, bearer);
    }

    private HttpResponse<String> put(String bearer, String applicationCode, String roleCode) {
        return api.putJson(ACTIVE + applicationCode, bearer, "{\"roleCode\":" + CompatSupport.q(roleCode) + "}");
    }

    private static Map<String, Object> body(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    private static String activeRoleCode(HttpResponse<String> response) {
        return activeRoleCode(body(response));
    }

    private static String activeRoleCode(Map<String, Object> body) {
        Object role = body.get("activeRole");
        return role == null ? null : (String) ((Map<?, ?>) role).get("code");
    }

    private static void expectOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
