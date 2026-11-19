package cv.igrp.platform.access_management.compat;

import cv.igrp.framework.auth.core.route.PermissionKeys;
import cv.igrp.platform.access_management.routes.domain.service.RouteRegistrationService;
import cv.igrp.platform.access_management.shared.security.SelfServicePaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AM's own routes on the key convention (v3-28, #36): AM registers its routes
 * in-process at startup, every one of them is closed until its key is granted,
 * and the legacy {@code igrp.*} assignments move to the keys through the fixed
 * mapping of {@code V25_1}.
 */
class AmOwnRoutesIT extends AbstractCompatIT {

    private static final String SERVICE = "access-management-api";
    private static final String APP = "APP_IGRP_CENTER";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transactions;

    private CompatSupport api;
    private String admin;
    private String suffix;

    @BeforeEach
    void setUp() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @AfterEach
    void retireDepartments() {
        // Shared database: the superadmin's departments are part of other tests' contract baselines.
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "AMD\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("AM registers a key for every route of its API except the self-service ones, in its own application")
    @SuppressWarnings("unchecked")
    void registersItsOwnRoutes() {
        Map<String, Object> paths = (Map<String, Object>) CompatSupport.jsonObject(
                api.get("/v3/api-docs", null).body()).get("paths");
        TreeSet<String> expected = new TreeSet<>();
        paths.forEach((path, methods) -> ((Map<String, Object>) methods).keySet().forEach(method -> {
            if (!SelfServicePaths.matches(method.toUpperCase(), path)) {
                expected.add(PermissionKeys.derive(SERVICE, method.toUpperCase(), path));
            }
        }));

        List<String> registered = jdbc.queryForList("""
                SELECT p.permission_key FROM t_permission p JOIN t_application a ON a.id = p.application_id
                 WHERE p.service = ? AND p.status = 'ACTIVE' AND a.code = ?""", String.class, SERVICE, APP);

        // 130 since #38 removed POST /api/authorize/check and batch-check.
        assertThat(expected).hasSize(130);
        assertThat(registered).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(jdbc.queryForObject("SELECT owner_client_id FROM t_registered_service WHERE service = ?",
                String.class, SERVICE)).isEqualTo(RouteRegistrationService.AM_OWNER);
    }

    @Test
    @DisplayName("No service can register under AM's own service name, even with the registration key")
    void amServiceNameIsReserved() {
        String token = api.serviceToken(api.registerM2MClient("am-squat-" + suffix.toLowerCase(), api.createApplication("AMSQ_" + suffix)));
        HttpResponse<String> registered = api.postJson("/api/routes/register", token,
                "{\"service\":\"" + SERVICE + "\",\"version\":\"99.0.0\",\"routes\":[]}");

        assertThat(registered.statusCode()).as(registered.body()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT owner_client_id FROM t_registered_service WHERE service = ?",
                String.class, SERVICE)).isEqualTo(RouteRegistrationService.AM_OWNER);
    }

    @Test
    @DisplayName("A route opens with its key and stays closed without it; self-service and public routes need none")
    void closedUntilGranted() {
        String userId = user();
        department("D1", "R1");
        assign(userId, "D1", "R1");
        String token = api.userToken(userId, email(userId));

        assertThat(api.get("/api/departments", token).statusCode()).isEqualTo(403);

        grant("R1", key("departments.list"));
        evict(userId);

        assertThat(api.get("/api/departments", token).statusCode()).isEqualTo(200);
        assertThat(api.get("/api/users", token).statusCode()).isEqualTo(403);
        assertThat(api.get("/api/users/me/permissions?applicationCode=" + APP, token).statusCode()).isEqualTo(200);
        assertThat(api.get("/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(api.get("/v3/api-docs", null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("V25_1 moves a role's and a service account's AM igrp.* permissions to their keys, then retires them")
    void legacyAssignmentsMove() throws IOException {
        String userId = user();
        department("D2", "R2");
        assign(userId, "D2", "R2");
        int view = legacyPermission("igrp.departments.view");
        int sync = legacyPermission("igrp.m2m.sync");
        // Another service's legacy permission: a business permission, left for the migration review (#37).
        int notification = legacyPermission("igrp.notification.send");
        jdbc.update("INSERT INTO t_role_permission (role_id, permission, scope) SELECT id, ?, 'own' FROM t_role WHERE code = ?",
                view, role("R2"));
        jdbc.update("INSERT INTO t_role_permission (role_id, permission, scope) SELECT id, ?, 'own' FROM t_role WHERE code = ?",
                notification, role("R2"));
        String serviceAccount = serviceAccount();
        jdbc.update("INSERT INTO t_service_account_permission_grant (permission_id, service_account_id, granted_at) "
                + "VALUES (?, ?::uuid, now())", sync, serviceAccount);

        migrate();
        evict(userId);

        assertThat(jdbc.queryForList("""
                SELECT p.permission_key || '=' || rp.scope FROM t_role_permission rp
                  JOIN t_role r ON r.id = rp.role_id JOIN t_permission p ON p.id = rp.permission
                 WHERE r.code = ? AND p.permission_key IS NOT NULL""", String.class, role("R2")))
                .hasSize(12)
                .contains(key("departments.roles.list") + "=own", key("departments.read") + "=own")
                // /api/m2m/roles was for service accounts only: a role does not get its key.
                .doesNotContain(key("m2m.roles.create") + "=own")
                .allMatch(row -> row.endsWith("=own"));
        assertThat(jdbc.queryForObject("SELECT status FROM t_permission WHERE id = ?", String.class, notification))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_permission WHERE permission = ?",
                Integer.class, notification)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT p.permission_key FROM t_service_account_permission_grant g JOIN t_permission p ON p.id = g.permission_id
                 WHERE g.service_account_id = ?::uuid""", String.class, serviceAccount))
                .contains(key("routes.register.create"), key("m2m.sync.applications.create"))
                .hasSize(6);
        assertThat(jdbc.queryForList("SELECT status FROM t_permission WHERE id IN (?, ?)", String.class, view, sync))
                .containsOnly("DELETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_permission WHERE permission IN (?, ?)",
                Integer.class, view, sync)).isZero();

        String token = api.userToken(userId, email(userId));
        assertThat(api.get("/api/departments/" + dept("D2") + "/roles", token).statusCode()).isEqualTo(200);
        assertThat(api.postJson("/api/departments", token, "{}").statusCode()).isEqualTo(403);

        // Idempotent: nothing left to convert.
        migrate();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id WHERE r.code = ?""",
                Integer.class, role("R2"))).isEqualTo(13); // 12 keys and the untouched notification permission
    }

    private void migrate() throws IOException {
        String script = new ClassPathResource("db/migration/V25_1__am_route_keys.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        transactions.executeWithoutResult(status -> jdbc.execute(script));
    }

    private int legacyPermission(String name) {
        Integer existing = jdbc.query("SELECT id FROM t_permission WHERE name = ? AND permission_key IS NULL",
                rs -> rs.next() ? rs.getInt(1) : null, name);
        if (existing != null) {
            jdbc.update("UPDATE t_permission SET status = 'ACTIVE' WHERE id = ?", existing);
            return existing;
        }
        return jdbc.queryForObject("""
                INSERT INTO t_permission (name, status, created_by, created_date)
                VALUES (?, 'ACTIVE', 'compat', now()) RETURNING id""", Integer.class, name);
    }

    private String serviceAccount() {
        CompatSupport.M2MClient client = api.registerM2MClient("am-own-" + suffix.toLowerCase(), null, List.of());
        return jdbc.queryForObject("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client c ON c.id = sa.oauth_client_id
                 WHERE c.client_id = ?""", String.class, client.clientId());
    }

    private String user() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'AM Routes User', 'ACTIVE')""", id, email(id), email(id));
        return id;
    }

    private static String email(String userId) {
        return "am-" + userId + "@example.cv";
    }

    private void department(String name, String roleName) {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", admin, "[" + CompatSupport.q(APP) + "]"));
        ok(api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role(roleName)) + ",\"name\":\"Role " + roleName + "\",\"status\":\"ACTIVE\"}"));
    }

    private void assign(String userId, String department, String roleName) {
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role(roleName)) + "]}"));
    }

    private void grant(String roleName, String permissionKey) {
        jdbc.update("""
                INSERT INTO t_role_permission (role_id, permission, scope)
                SELECT r.id, p.id, 'department' FROM t_role r, t_permission p WHERE r.code = ? AND p.permission_key = ?""",
                role(roleName), permissionKey);
    }

    /** The test writes assignments in SQL, past the eviction after commit: drop the cached set by hand. */
    private void evict(String userId) {
        context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class)
                .delete("permissions:user:" + userId + ":app:" + APP);
    }

    private static String key(String rest) {
        return SERVICE + "." + rest;
    }

    private String role(String name) {
        return "AMR_" + name + "_" + suffix;
    }

    private String dept(String name) {
        return "AMD_" + name + "_" + suffix;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
