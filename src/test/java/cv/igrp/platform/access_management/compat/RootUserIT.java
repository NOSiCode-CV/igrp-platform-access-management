package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.authorization.domain.service.PermissionCheckService;
import cv.igrp.platform.access_management.shared.infrastructure.service.RootUser;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-12 (#20): root is exactly one user, the one whose email is
 * {@code IGRP_SUPERADMIN_EMAIL}. Every startup makes it so; no endpoint can
 * assign the super-admin role.
 */
class RootUserIT extends AbstractCompatIT {

    private static final String ROOT_EMAIL = CompatEnvironment.SUPERADMIN_EMAIL;
    private static final String ROOT_ROLE = "DEPT_IGRP.superadmin";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    RootUser root;
    @Autowired
    PermissionCheckService permissionCheck;

    private CompatSupport api;
    private String admin;
    private String suffix;

    @BeforeEach
    void setUp() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    }

    @AfterEach
    void restoreTheConfiguredRootAndRetireTestUsers() {
        root.sync(ROOT_EMAIL, CompatEnvironment.SUPERADMIN_ID);
        // Look-alike roles made by a test (sync has already stripped their holders):
        // other tests look the real role up, and must find one.
        jdbc.update("""
                DELETE FROM t_role WHERE code = ? AND department IN
                       (SELECT id FROM t_department WHERE code <> 'DEPT_IGRP')""", ROOT_ROLE);
        jdbc.update("UPDATE t_user SET status = 'DELETED' WHERE lower(email) LIKE ? AND id <> ?",
                "%-" + suffix + "@example.cv", api.superadminId());
    }

    @Test
    @DisplayName("At startup, the user with the configured email is root: ACTIVE and holding the role")
    void configuredUserIsRoot() {
        assertThat(rootHolders()).containsExactly(api.superadminId());
        assertThat(status(api.superadminId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT lower(email) FROM t_user WHERE id = ?", String.class,
                api.superadminId())).isEqualTo(ROOT_EMAIL);
    }

    @Test
    @DisplayName("A new email creates that user ACTIVE as root; the previous root loses it, its keys go, both audited")
    void changingTheEmailMovesRoot() {
        String previous = api.superadminId();
        String application = "RT_APP_" + suffix.toUpperCase();
        api.createApplication(application);
        assertThat(api.get("/api/users/me/permissions?applicationCode=" + application, admin).statusCode())
                .isEqualTo(200);
        assertThat(redis.hasKey(setKey(previous, application))).isTrue();
        String email = "root-" + suffix + "@example.cv";
        long unassignedBefore = audited("UNASSIGN", previous);

        root.sync(email, null);

        String next = userByEmail(email);
        assertThat(status(next)).as("created ACTIVE, no invitation").isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT username FROM t_user WHERE id = ?", String.class, next))
                .as("without an external id, the username is the email").isEqualTo(email);
        assertThat(rootHolders()).containsExactly(next);
        assertThat(activeRoleIsRoot(next)).as("root's active role is pinned").isTrue();
        assertThat(redis.hasKey(setKey(previous, application))).as("the previous root's keys").isFalse();
        assertThat(activeRoleIsRoot(previous)).as("the previous root's active role is cleared").isFalse();
        assertThat(audited("UNASSIGN", previous) - unassignedBefore).as("audit rows are append-only").isOne();
        assertThat(audited("ASSIGN", next)).isOne();
    }

    @Test
    @DisplayName("An existing user with the email becomes root, and is made ACTIVE")
    void existingUserBecomesRoot() {
        String email = "existing-" + suffix + "@example.cv";
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Future Root', 'TEMPORARY')""", id, email.toUpperCase(), email);

        root.sync(email, null);

        assertThat(rootHolders()).containsExactly(id);
        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_user WHERE lower(email) = ? AND status <> 'DELETED'",
                Long.class, email)).as("no second user").isOne();
    }

    @Test
    @DisplayName("Startup takes the super-admin role away from every service account")
    void serviceAccountsLoseRoot() {
        CompatSupport.M2MClient client = api.registerM2MClient("rt-" + suffix);
        String serviceAccount = serviceAccountId(client);
        jdbc.update("""
                INSERT INTO t_service_account_role_assignment (service_account_id, role_id, assigned_at)
                SELECT ?::uuid, id, now() FROM t_role WHERE code = ?""", serviceAccount, ROOT_ROLE);

        root.sync(ROOT_EMAIL, CompatEnvironment.SUPERADMIN_ID);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_service_account_role_assignment sara JOIN t_role r ON r.id = sara.role_id
                 WHERE sara.service_account_id = ?::uuid AND r.code = ?""", Long.class, serviceAccount, ROOT_ROLE))
                .isZero();
    }

    @Test
    @DisplayName("Assigning the super-admin role to a user is refused")
    void addRolesRefusesRoot() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Not Root', 'ACTIVE')""",
                id, "not-root-" + suffix + "@example.cv", "not-root-" + suffix + "@example.cv");

        HttpResponse<String> response = api.postJson("/api/users/" + id + "/departments/DEPT_IGRP/roles", admin,
                "{\"roles\":[\"" + ROOT_ROLE + "\"]}");

        assertRefused(response, "IGRP_AUTH_ROLE_SUPERADMIN_NOT_ASSIGNABLE");
        assertThat(rootHolders()).containsExactly(api.superadminId());
    }

    @Test
    @DisplayName("Inviting someone with the super-admin role is refused")
    void inviteRefusesRoot() {
        HttpResponse<String> response = api.postJson("/api/users/invite", admin,
                "{\"email\":\"invitee-" + suffix + "@example.cv\",\"departmentCode\":\"DEPT_IGRP\","
                        + "\"roles\":[\"" + ROOT_ROLE + "\"]}");

        assertRefused(response, "IGRP_AUTH_ROLE_SUPERADMIN_NOT_ASSIGNABLE");
    }

    @Test
    @DisplayName("Giving a service account the super-admin role is refused")
    void serviceAccountUpdateRefusesRoot() {
        CompatSupport.M2MClient client = api.registerM2MClient("rt-sa-" + suffix);
        String serviceAccount = serviceAccountId(client);
        Map<String, Object> current = CompatSupport.jsonObject(
                api.get("/api/service-accounts/" + serviceAccount, admin).body());
        Integer rootRoleId = jdbc.queryForObject("""
                SELECT r.id FROM t_role r JOIN t_department d ON d.id = r.department
                 WHERE r.code = ? AND d.code = 'DEPT_IGRP'""", Integer.class, ROOT_ROLE);

        HttpResponse<String> response = api.putJson("/api/service-accounts/" + serviceAccount, admin,
                "{\"name\":" + CompatSupport.q((String) current.get("name"))
                        + ",\"oauthClientId\":\"" + oauthClientId(client) + "\",\"roleIds\":[" + rootRoleId + "]}");

        assertRefused(response, "IGRP_AUTH_ROLE_SUPERADMIN_NOT_ASSIGNABLE");
    }

    @Test
    @DisplayName("A role named like the super-admin role cannot be created in another department")
    void lookAlikeRoleRefused() {
        String department = "RT_D_" + suffix.toUpperCase();
        assertThat(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(department)
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}").statusCode()).isBetween(200, 299);

        HttpResponse<String> response = api.postJson("/api/departments/" + department + "/roles", admin,
                "{\"code\":\"" + ROOT_ROLE + "\",\"name\":\"Look-alike\",\"status\":\"ACTIVE\"}");

        assertRefused(response, "IGRP_AUTH_ROLE_SUPERADMIN_NOT_ASSIGNABLE");
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", department);
    }

    @Test
    @DisplayName("A look-alike role that already exists grants no root, and startup strips it")
    void existingLookAlikeGrantsNothing() {
        String department = "RT_L_" + suffix.toUpperCase();
        jdbc.update("""
                INSERT INTO t_department (code, name, status, created_by, created_date)
                VALUES (?, 'Look-alike', 'ACTIVE', 'compat', now())""", department);
        jdbc.update("""
                INSERT INTO t_role (code, name, status, department, created_by, created_date)
                SELECT ?, 'Look-alike', 'ACTIVE', id, 'compat', now() FROM t_department WHERE code = ?""",
                ROOT_ROLE, department);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Look-alike Holder', 'ACTIVE')""",
                id, "lookalike-" + suffix + "@example.cv", "lookalike-" + suffix + "@example.cv");
        jdbc.update("""
                INSERT INTO t_user_role_assignment (user_id, role_id, assigned_at)
                SELECT ?, r.id, now() FROM t_role r JOIN t_department d ON d.id = r.department
                 WHERE r.code = ? AND d.code = ?""", id, ROOT_ROLE, department);

        assertThat(permissionCheck.isSuperAdmin(id)).as("a look-alike is not root").isFalse();

        root.sync(ROOT_EMAIL, CompatEnvironment.SUPERADMIN_ID);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_user_role_assignment WHERE user_id = ?",
                Long.class, id)).as("startup strips the look-alike").isZero();
        assertThat(rootHolders()).containsExactly(api.superadminId());
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code = ?", department);
    }

    @Test
    @DisplayName("A DELETED user still holding the email as username does not break the handover")
    void deletedUsernameHolderDoesNotBreakTheHandover() {
        String email = "reborn-" + suffix + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Gone', 'DELETED')""", UUID.randomUUID().toString(), email, email);

        root.sync(email, null);

        assertThat(rootHolders()).containsExactly(userByEmail(email));
    }

    @Test
    @DisplayName("Root's role cannot be taken away, nor root disabled, through the API")
    void rootIsProtected() {
        String rootId = api.superadminId();

        assertRefused(api.deleteJson("/api/users/" + rootId + "/departments/DEPT_IGRP/roles", admin,
                "[\"" + ROOT_ROLE + "\"]"), "IGRP_AUTH_ROOT_PROTECTED");
        assertRefused(api.putJson("/api/users/" + rootId + "/status?value=INACTIVE", admin, ""),
                "IGRP_AUTH_ROOT_PROTECTED");
        assertThat(rootHolders()).containsExactly(rootId);
        assertThat(status(rootId)).isEqualTo("ACTIVE");
    }

    // ---- fixtures ----

    private static void assertRefused(HttpResponse<String> response, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains(code);
    }

    private List<String> rootHolders() {
        return jdbc.queryForList("""
                SELECT ura.user_id FROM t_user_role_assignment ura JOIN t_role r ON r.id = ura.role_id
                  JOIN t_department d ON d.id = r.department
                 WHERE r.code = ? AND d.code = 'DEPT_IGRP'""", String.class, ROOT_ROLE);
    }

    private String status(String userId) {
        return jdbc.queryForObject("SELECT status FROM t_user WHERE id = ?", String.class, userId);
    }

    private String userByEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM t_user WHERE lower(email) = ? AND status <> 'DELETED'",
                String.class, email);
    }

    private boolean activeRoleIsRoot(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM t_user u JOIN t_role r ON r.id = u.active_role_id
                                WHERE u.id = ? AND r.code = ?)""", Boolean.class, userId, ROOT_ROLE));
    }

    /** Settings audit rows for the root role on one user, by operation. */
    private long audited(String operation, String userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log
                 WHERE settings_operation = ? AND entity_name = ? AND related_entity = ?""",
                Long.class, operation, ROOT_ROLE, userId);
    }

    private String serviceAccountId(CompatSupport.M2MClient client) {
        return jdbc.queryForList("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                 WHERE oc.client_id = ?""", String.class, client.clientId()).getFirst();
    }

    private String oauthClientId(CompatSupport.M2MClient client) {
        return jdbc.queryForList("SELECT id::text FROM t_oauth_client WHERE client_id = ?", String.class,
                client.clientId()).getFirst();
    }

    private static String setKey(String sub, String application) {
        return "permissions:user:" + sub + ":app:" + application;
    }
}
