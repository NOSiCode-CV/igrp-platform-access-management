package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.routes.domain.service.PendingKeyDeletions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-03 (#11): dead permissions disappear safely, and a renamed service keeps
 * its access.
 * <ul>
 *   <li>A registration from the newest version (semver) schedules the keys it
 *       no longer declares for deletion after a grace period. A job deletes
 *       them when due: unassigned everywhere, audited, cache evicted.</li>
 *   <li>An older version never schedules or deletes. It creates the keys it is
 *       missing (a hotfix on a support branch) and cancels the pending deletion
 *       of the keys it still declares (a rollback, or an old pod restarting).</li>
 *   <li>A known {@code client_id} under a new service name is a rename: keys are
 *       rewritten with the new prefix and keep every assignment.</li>
 * </ul>
 */
class RouteVersionGateIT extends AbstractCompatIT {

    private static final String REGISTER = "/api/routes/register";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    PendingKeyDeletions pendingDeletions;

    private CompatSupport api;
    private String admin;
    private String service;
    private String application;
    private String token;
    private String department;
    private String role;

    @BeforeEach
    void serviceWithItsOwnClientAndARole() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        service = "vg-" + suffix;
        application = "VG_APP_" + suffix.toUpperCase();
        int applicationId = api.createApplication(application);
        token = api.serviceToken(api.registerM2MClient(service + "-client", applicationId));
        department = "VG_D_" + suffix.toUpperCase();
        role = "VG_R_" + suffix.toUpperCase();
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(department)
                + ",\"name\":\"Dept\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + department + "/applications", admin,
                "[" + CompatSupport.q(application) + "]"));
        ok(api.postJson("/api/departments/" + department + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role) + ",\"name\":\"Role\",\"status\":\"ACTIVE\"}"));
    }

    @Test
    @DisplayName("The newest version schedules the keys it dropped; nothing is deleted before the grace period")
    void newestVersionSchedulesDeletion() {
        ok(register("1.0.0", "a", "b"));
        grant("a", "b");

        HttpResponse<String> response = register("2.0.0", "a");

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("deleted", 1);
        assertThat(pending()).containsExactly(key("b"));
        pendingDeletions.deleteDue();
        assertThat(status("b")).as("not due yet").isEqualTo("ACTIVE");
        assertThat(assignments("b")).isEqualTo(2);
    }

    @Test
    @DisplayName("When due, the job unassigns the key everywhere, deletes it, audits it and evicts the holders' keys")
    void dueDeletionRuns() {
        ok(register("1.0.0", "a", "b"));
        grant("a", "b");
        String userId = userWithTheRole();
        assertThat(api.get("/api/users/me/permissions?applicationCode=" + application,
                api.userToken(userId, userId + "@example.cv")).statusCode()).isEqualTo(200);
        String set = "permissions:user:" + userId + ":app:" + application;
        assertThat(redis.opsForSet().members(set)).contains(key("b"));
        ok(register("2.0.0", "a"));

        makeDue();
        pendingDeletions.deleteDue();

        assertThat(status("b")).isEqualTo("DELETED");
        assertThat(assignments("b")).isZero();
        assertThat(pending()).isEmpty();
        assertThat(status("a")).isEqualTo("ACTIVE");
        assertThat(assignments("a")).isEqualTo(2);
        assertThat(redis.hasKey(set)).as("the holder's set is evicted").isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log WHERE entity_name = ? AND settings_operation = 'DELETE'""",
                Long.class, key("b"))).isOne();
    }

    @Test
    @DisplayName("An older version never schedules: a registration that is not the newest leaves the rest alone")
    void olderVersionNeverSchedules() {
        ok(register("2.0.0", "a", "b"));

        HttpResponse<String> response = register("1.0.0", "a");

        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("deleted", 0);
        assertThat(pending()).isEmpty();
    }

    @Test
    @DisplayName("An older version cancels only the pending deletions of the keys it still declares")
    void olderVersionCancelsOnlyWhatItDeclares() {
        ok(register("1.0.0", "a", "b"));
        ok(register("2.0.0", "a", "c"));
        ok(register("3.0.0", "a"));
        assertThat(pending()).containsExactlyInAnyOrder(key("b"), key("c"));

        // A 2.0.0 pod restarting mid-deploy still serves c, not b.
        ok(register("2.0.0", "a", "c"));

        assertThat(pending()).containsExactly(key("b"));
        makeDue();
        pendingDeletions.deleteDue();
        assertThat(status("b")).isEqualTo("DELETED");
        assertThat(status("c")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A hotfix on an older version creates its new keys, and deletes nothing")
    void olderHotfixCreatesItsKeys() {
        ok(register("2.0.0", "a", "b"));

        HttpResponse<String> response = register("1.4.1", "a", "hotfix");

        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("created", 1);
        assertThat(status("hotfix")).isEqualTo("ACTIVE");
        assertThat(pending()).isEmpty();
        assertThat(status("b")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A newest-version key declared again is no longer pending")
    void declaredAgainIsNotPending() {
        ok(register("1.0.0", "a", "b"));
        ok(register("2.0.0", "a"));

        ok(register("2.0.1", "a", "b"));

        assertThat(pending()).isEmpty();
    }

    @Test
    @DisplayName("A rollback after the grace period brings the key back, with no assignments")
    void rollbackAfterGraceRecreatesWithoutAssignments() {
        ok(register("1.0.0", "a", "b"));
        grant("b");
        ok(register("2.0.0", "a"));
        makeDue();
        pendingDeletions.deleteDue();
        assertThat(status("b")).isEqualTo("DELETED");

        HttpResponse<String> response = register("1.0.0", "a", "b");

        assertThat(CompatSupport.jsonObject(response.body())).containsEntry("created", 1);
        assertThat(status("b")).isEqualTo("ACTIVE");
        assertThat(assignments("b")).as("someone has to grant it again").isZero();
    }

    @Test
    @DisplayName("A rename by the same client keeps every assignment under the new prefix")
    void renameKeepsAssignments() {
        ok(register("1.0.0", "a", "b"));
        grant("a", "b");
        String renamed = service + "-new";

        HttpResponse<String> response = registerAs(renamed, "1.0.0", renamed + ".a.read", renamed + ".b.read");

        ok(response);
        assertThat(CompatSupport.jsonObject(response.body()))
                .containsEntry("renamed", 2).containsEntry("created", 0).containsEntry("deleted", 0);
        assertThat(count(key("a"))).as("the old key is gone").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_role_permission rp JOIN t_permission p ON p.id = rp.permission
                 WHERE p.permission_key IN (?, ?)""", Long.class, renamed + ".a.read", renamed + ".b.read"))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT service FROM t_permission WHERE permission_key = ?", String.class,
                renamed + ".a.read")).isEqualTo(renamed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_registered_service WHERE service = ?", Long.class,
                service)).as("the old name is released").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_security_audit_log WHERE settings_operation = 'EDIT'
                   AND previous_value = ? AND new_value = ?""", Long.class, key("a"), renamed + ".a.read"))
                .isOne();
    }

    @Test
    @DisplayName("An older version under the old name cannot rename the service back")
    void olderVersionCannotRenameBack() {
        ok(register("1.0.0", "a"));
        String renamed = service + "-new";
        ok(registerAs(renamed, "2.0.0", renamed + ".a.read"));

        HttpResponse<String> response = registerAs(service, "1.0.0", key("a"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(count(renamed + ".a.read")).isOne();
        assertThat(count(key("a"))).isZero();
    }

    @Test
    @DisplayName("A rename that would overwrite existing keys is refused, and nothing moves")
    void renameCollisionRefused() {
        ok(register("1.0.0", "a"));
        String renamed = service + "-taken";
        jdbc.update("""
                INSERT INTO t_permission (created_by, created_date, name, permission_key, service, status)
                VALUES ('compat', now(), ?, ?, 'someone-else', 'ACTIVE')""", renamed + ".a.read", renamed + ".a.read");

        HttpResponse<String> response = registerAs(renamed, "1.0.0", renamed + ".a.read");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(count(key("a"))).isOne();
    }

    @Test
    @DisplayName("A rename carries the pending deletions with it")
    void renameCarriesPendingDeletions() {
        ok(register("1.0.0", "a", "b"));
        ok(register("2.0.0", "a"));
        String renamed = service + "-moved";

        ok(registerAs(renamed, "2.0.0", renamed + ".a.read"));

        assertThat(jdbc.queryForList("""
                SELECT p.permission_key FROM t_permission_pending_deletion pd JOIN t_permission p ON p.id = pd.permission_id
                 WHERE pd.service = ?""", String.class, renamed)).containsExactly(renamed + ".b.read");
    }

    @Test
    @DisplayName("A deleted key's direct grant to a service account goes too")
    void serviceAccountGrantRemoved() {
        ok(register("1.0.0", "a", "b"));
        CompatSupport.M2MClient caller = api.registerM2MClient(service + "-caller", null, List.of(key("b")));
        String serviceAccount = jdbc.queryForList("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                 WHERE oc.client_id = ?""", String.class, caller.clientId()).getFirst();
        ok(register("2.0.0", "a"));

        makeDue();
        pendingDeletions.deleteDue();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_service_account_permission_grant WHERE service_account_id = ?::uuid",
                Long.class, serviceAccount)).isZero();
    }

    // ---- fixtures ----

    private HttpResponse<String> register(String version, String... actions) {
        String[] keys = Arrays.stream(actions).map(this::key).toArray(String[]::new);
        return registerAs(service, version, keys);
    }

    private HttpResponse<String> registerAs(String serviceName, String version, String... keys) {
        StringBuilder routes = new StringBuilder();
        for (String key : keys) {
            String[] segments = key.split("\\.");
            routes.append(routes.isEmpty() ? "" : ",")
                    .append("{\"method\":\"GET\",\"path\":\"/api/v1/").append(segments[segments.length - 2])
                    .append("\",\"key\":").append(CompatSupport.q(key)).append(",\"annotated\":true}");
        }
        return api.postJson(REGISTER, token, "{\"service\":" + CompatSupport.q(serviceName)
                + ",\"version\":" + CompatSupport.q(version) + ",\"routes\":[" + routes + "]}");
    }

    /** Grants the keys to the department and its role: two assignments each. */
    private void grant(String... actions) {
        StringBuilder keys = new StringBuilder();
        for (String action : actions) {
            keys.append(keys.isEmpty() ? "" : ",").append(CompatSupport.q(key(action)));
        }
        ok(api.postJson("/api/departments/" + department + "/permissions", admin, "[" + keys + "]"));
        ok(api.postJson("/api/departments/" + department + "/roles/" + role + "/permissions", admin,
                "[" + keys + "]"));
    }

    private String userWithTheRole() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Gate User', 'ACTIVE')""", id, id + "@example.cv", id + "@example.cv");
        ok(api.postJson("/api/users/" + id + "/departments/" + department + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role) + "]}"));
        return id;
    }

    private void makeDue() {
        jdbc.update("""
                UPDATE t_permission_pending_deletion SET due_at = now() - interval '1 second'
                 WHERE permission_id IN (SELECT id FROM t_permission WHERE service = ?)""", service);
    }

    private List<String> pending() {
        return jdbc.queryForList("""
                SELECT p.permission_key FROM t_permission_pending_deletion pd JOIN t_permission p ON p.id = pd.permission_id
                 WHERE p.service = ? ORDER BY 1""", String.class, service);
    }

    private String status(String action) {
        return jdbc.queryForObject("SELECT status FROM t_permission WHERE permission_key = ?", String.class, key(action));
    }

    private long assignments(String action) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM t_role_permission rp WHERE rp.permission = p.id)
                     + (SELECT count(*) FROM t_permission_department pd WHERE pd.permission_id = p.id)
                  FROM t_permission p WHERE p.permission_key = ?""", Long.class, key(action));
    }

    private long count(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM t_permission WHERE permission_key = ?", Long.class, key);
    }

    /** A key per resource name, all with the read action. */
    private String key(String name) {
        return service + "." + name + ".read";
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
