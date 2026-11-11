package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-10 (#18): a change to what a user may do takes effect at their next
 * request. AM deletes the user's permission sets, found through the per-user
 * index, only after the change commits.
 */
class PermissionSetEvictionIT extends AbstractCompatIT {

    private static final String FETCH = "/api/users/me/permissions?applicationCode=";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    ExpireRoleService expireRoleService;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String service;
    private String app;
    private String userId;
    private String email;
    private String token;

    @BeforeEach
    void userWithOneRoleAndAFilledSet() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service = "ev-" + suffix.toLowerCase();
        app = "EV_APP_" + suffix;
        int appId = api.createApplication(app);
        String serviceToken = api.serviceToken(api.registerM2MClient(service + "-client", appId));
        ok(api.postJson("/api/routes/register", serviceToken,
                "{\"service\":" + CompatSupport.q(service) + ",\"version\":\"1.0.0\",\"routes\":["
                        + route("GET", "/api/v1/faturas", key("list")) + ","
                        + route("POST", "/api/v1/faturas", key("create")) + "]}"));

        userId = UUID.randomUUID().toString();
        email = "ev-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Eviction User', 'ACTIVE')""", userId, email, email);

        department("D1", "R1");
        ok(api.postJson("/api/departments/" + dept("D1") + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "," + CompatSupport.q(key("create")) + "]"));
        ok(api.postJson("/api/departments/" + dept("D1") + "/roles/" + role("R1") + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "]"));
        assign("D1", "R1");
        token = api.userToken(userId, email);

        assertThat(fetch()).containsExactly(key("list"));
        assertThat(redis.hasKey(setKey())).isTrue();
    }

    @AfterEach
    void retireDepartments() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "EV\\_%\\_" + suffix);
    }

    @Test
    @DisplayName("Assigning a role deletes the set after commit; the next fetch reads the new roles")
    void roleAssigned() {
        department("D2", "R2");

        assign("D2", "R2");

        assertEvicted();
        assertThat(refetch()).as("two roles and no choice yet: nothing until the user picks one").isEmpty();
    }

    @Test
    @DisplayName("Removing a role deletes the set after commit")
    void roleRemoved() {
        ok(api.deleteJson("/api/users/" + userId + "/departments/" + dept("D1") + "/roles", admin,
                "[" + CompatSupport.q(role("R1")) + "]"));

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("A temporary role that expires deletes the set after the reaper commits")
    void temporaryRoleExpired() {
        jdbc.update("UPDATE t_user_role_assignment SET expires_at = now() - interval '1 minute' WHERE user_id = ?",
                userId);

        expireRoleService.removeExpiredRoles();

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("Adding a permission to the role deletes the set after commit")
    void permissionAddedToRole() {
        ok(api.postJson("/api/departments/" + dept("D1") + "/roles/" + role("R1") + "/permissions", admin,
                "[" + CompatSupport.q(key("create")) + "]"));

        assertEvicted();
        assertThat(refetch()).containsExactlyInAnyOrder(key("list"), key("create"));
    }

    @Test
    @DisplayName("Removing a permission from the role deletes the set after commit")
    void permissionRemovedFromRole() {
        ok(api.deleteJson("/api/departments/" + dept("D1") + "/roles/" + role("R1") + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "]"));

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("Changing the department's permissions deletes the set of every user in it")
    void departmentChanged() {
        ok(api.deleteJson("/api/departments/" + dept("D1") + "/permissions", admin,
                "[" + CompatSupport.q(key("list")) + "]"));

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("Deactivating the role deletes the set after commit")
    void roleDeactivated() {
        ok(api.putJson("/api/departments/" + dept("D1") + "/roles/" + role("R1"), admin,
                "{\"code\":" + CompatSupport.q(role("R1")) + ",\"name\":\"Role R1\",\"status\":\"INACTIVE\"}"));

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("Deactivating a parent department deletes the sets of users whose roles are in a child")
    void parentDepartmentDeactivated() {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept("P")) + ",\"name\":\"Dept P\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept("P") + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        // The update endpoint does not move a department; the fixture does.
        jdbc.update("UPDATE t_department SET parent_id = (SELECT id FROM t_department WHERE code = ?) WHERE code = ?",
                dept("P"), dept("D1"));

        ok(api.putJson("/api/departments/" + dept("P"), admin,
                "{\"code\":" + CompatSupport.q(dept("P")) + ",\"name\":\"Dept P\",\"status\":\"INACTIVE\"}"));

        assertEvicted();
    }

    @Test
    @DisplayName("Adding the application to another department of the user deletes the set after commit")
    void applicationAddedToDepartment() {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept("D2")) + ",\"name\":\"Dept D2\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept("D2") + "/roles", admin,
                "{\"code\":" + CompatSupport.q(role("R2")) + ",\"name\":\"Role R2\",\"status\":\"ACTIVE\"}"));
        assign("D2", "R2");
        assertThat(refetch()).as("R2's department has no access to the app: R1 stays the only role").containsExactly(key("list"));

        ok(api.postJson("/api/departments/" + dept("D2") + "/applications", admin, "[" + CompatSupport.q(app) + "]"));

        assertEvicted();
        assertThat(refetch()).as("two valid roles and no choice yet").isEmpty();
    }

    @Test
    @DisplayName("Deactivating the application deletes the sets of its departments' users after commit")
    void applicationDeactivated() {
        ok(api.putJson("/api/applications/" + app, admin, "{\"code\":" + CompatSupport.q(app)
                + ",\"name\":\"App\",\"type\":\"INTERNAL\",\"status\":\"INACTIVE\"}"));

        assertEvicted();
    }

    @Test
    @DisplayName("Deleting a permission deletes the set of every user who held it, after commit")
    void permissionDeleted() {
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE t_permission SET status = 'DELETED' WHERE permission_key = ?", key("list"));
            events.publishEvent(new DeletePermissionEvent(this, key("list")));
        });

        assertEvicted();
        assertThat(refetch()).isEmpty();
    }

    @Test
    @DisplayName("Switching the global active role deletes the user's sets after commit")
    void globalActiveRoleSwitched() {
        ok(api.postJson("/api/users/me/roles/active", token,
                "{\"roleCode\":" + CompatSupport.q(role("R1")) + ",\"departmentCode\":" + CompatSupport.q(dept("D1"))
                        + "}"));

        assertEvicted();
        assertThat(refetch()).containsExactly(key("list"));
    }

    @Test
    @DisplayName("Disabling the user deletes their sets; the next fetch is 401")
    void userDisabled() {
        ok(api.putJson("/api/users/" + userId + "/status?value=INACTIVE", admin, ""));

        assertEvicted();
        assertThat(api.get(FETCH + app, token).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Every application of the user is deleted, found through the index, and the index with them")
    void everyApplicationThroughTheIndex() {
        String other = "EV_OTHER_" + suffix;
        api.createApplication(other);
        assertThat(api.get(FETCH + other, token).statusCode()).isEqualTo(200);
        String otherKey = "permissions:user:" + userId + ":app:" + other;
        assertThat(redis.hasKey(otherKey)).isTrue();

        ok(api.putJson("/api/users/" + userId + "/status?value=INACTIVE", admin, ""));

        assertThat(redis.hasKey(setKey())).isFalse();
        assertThat(redis.hasKey(otherKey)).isFalse();
        assertThat(redis.hasKey(indexKey())).isFalse();
    }

    @Test
    @DisplayName("Nothing is deleted before the transaction commits")
    void notBeforeCommit() {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(roleChanged());
            assertThat(redis.hasKey(setKey())).as("still there inside the transaction").isTrue();
        });

        assertEvicted();
    }

    @Test
    @DisplayName("A rolled-back change deletes nothing")
    void rolledBackChangeDeletesNothing() {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(roleChanged());
            status.setRollbackOnly();
        });

        assertThat(redis.hasKey(setKey())).isTrue();
        assertThat(redis.opsForSet().members(indexKey())).contains(setKey());
    }

    // ---- fixtures ----

    private UserRoleChangedEvent roleChanged() {
        return new UserRoleChangedEvent(userId, Set.of(role("R1")), dept("D1"), UserRoleChangedEvent.CHANGE_ADDED,
                "compat");
    }

    private void assertEvicted() {
        assertThat(redis.hasKey(setKey())).as("the set is deleted after commit").isFalse();
        assertThat(redis.hasKey(setKey() + ":scopes")).isFalse();
    }

    private String key(String action) {
        return service + ".faturas." + action;
    }

    private String role(String name) {
        return "EV_" + name + "_" + suffix;
    }

    private String dept(String name) {
        return "EV_" + name + "_" + suffix;
    }

    private String setKey() {
        return "permissions:user:" + userId + ":app:" + app;
    }

    private String indexKey() {
        return "permissions:user:" + userId + ":apps";
    }

    private static String route(String method, String path, String key) {
        return "{\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"key\":\"" + key + "\",\"annotated\":false}";
    }

    private void department(String name, String... roles) {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\"}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        for (String r : roles) {
            ok(api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}"));
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

    /**
     * The fetch after a change, with a new token: until sessions are removed
     * (v3 "Removed from AM 0.3"), the same change also revokes the user's
     * 0.2 session, so the old token is refused.
     */
    private List<String> refetch() {
        token = api.userToken(userId, email);
        return fetch();
    }

    @SuppressWarnings("unchecked")
    private List<String> fetch() {
        HttpResponse<String> response = api.get(FETCH + app, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Map<String, Object> body = CompatSupport.jsonObject(response.body());
        return (List<String>) body.get("keys");
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
