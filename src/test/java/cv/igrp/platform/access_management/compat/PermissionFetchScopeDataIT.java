package cv.igrp.platform.access_management.compat;

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
 * v3-31 (#39): the permission fetch also gives the data the starter's scope
 * filter compares rows with: the caller's role codes ({@code by_role}) and
 * the codes of its visible department subtree ({@code department}), in the
 * body and in two Redis sets next to the permission set, with its TTL.
 */
class PermissionFetchScopeDataIT extends AbstractCompatIT {

    private static final String FETCH = "/api/users/me/permissions?applicationCode=";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;

    private CompatSupport api;
    private String admin;
    private String suffix;
    private String app;
    private String userId;
    private String email;

    @BeforeEach
    void departmentTreeAndUser() {
        api = new CompatSupport(context);
        admin = api.superadminToken();
        suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        app = "SD_APP_" + suffix;
        api.createApplication(app);
        // D1 > D1C > D1CC, and a separate D2.
        department("D1", null, "R1");
        department("D1C", "D1");
        department("D1CC", "D1C");
        department("D2", null, "R2");

        userId = UUID.randomUUID().toString();
        email = "sd-" + suffix.toLowerCase() + "@example.cv";
        jdbc.update("""
                INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
                VALUES (?, 'compat', now(), ?, ?, 'Scope Data User', 'ACTIVE')""", userId, email, email);
    }

    @AfterEach
    void retire() {
        jdbc.update("UPDATE t_department SET status = 'DELETED' WHERE code LIKE ?", "SD\\_%\\_" + suffix);
        jdbc.update("UPDATE t_user SET status = 'DELETED' WHERE id = ?", userId);
    }

    @Test
    @DisplayName("The active role's code and its department subtree, in the body and in Redis with the set's TTL")
    void rolesAndDepartmentSubtree() {
        assign("D1", "R1");

        Map<String, Object> body = fetch(token());

        assertThat(list(body, "roles")).containsExactly(role("R1"));
        assertThat(list(body, "departments")).containsExactlyInAnyOrder(dept("D1"), dept("D1C"), dept("D1CC"));
        assertThat(redis.opsForSet().members(setKey() + ":roles")).containsExactly(role("R1"));
        assertThat(redis.opsForSet().members(setKey() + ":departments"))
                .containsExactlyInAnyOrder(dept("D1"), dept("D1C"), dept("D1CC"));
        long ttl = redis.getExpire(setKey());
        assertThat(redis.getExpire(setKey() + ":roles")).isBetween(ttl - 5, ttl);
        assertThat(redis.getExpire(setKey() + ":departments")).isBetween(ttl - 5, ttl);
    }

    @Test
    @DisplayName("Only the active role counts: the other role's department is not visible")
    void onlyTheActiveRole() {
        assign("D1", "R1");
        assign("D2", "R2");
        String token = token();
        ok(api.putJson("/api/users/me/role/active?applicationCode=" + app, token,
                "{\"roleCode\":" + CompatSupport.q(role("R2")) + "}"));

        Map<String, Object> body = fetch(token);

        assertThat(list(body, "roles")).containsExactly(role("R2"));
        assertThat(list(body, "departments")).containsExactly(dept("D2"));
    }

    @Test
    @DisplayName("No active role: no roles, no departments, and no Redis sets for them")
    void noActiveRole() {
        assign("D1", "R1");
        assign("D2", "R2");

        Map<String, Object> body = fetch(token());

        assertThat(list(body, "roles")).isEmpty();
        assertThat(list(body, "departments")).isEmpty();
        assertThat(redis.hasKey(setKey())).as("the permission set itself is written").isTrue();
        assertThat(redis.hasKey(setKey() + ":roles")).isFalse();
        assertThat(redis.hasKey(setKey() + ":departments")).isFalse();
    }

    @Test
    @DisplayName("A service account: every role it holds, and the union of their department subtrees")
    void serviceAccount() {
        CompatSupport.M2MClient client = api.registerM2MClient("sd-" + suffix.toLowerCase());
        String serviceAccount = jdbc.queryForList("""
                SELECT sa.id::text FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                 WHERE oc.client_id = ?""", String.class, client.clientId()).getFirst();
        for (String r : List.of("R1", "R2")) {
            jdbc.update("""
                    INSERT INTO t_service_account_role_assignment (service_account_id, role_id, assigned_at)
                    SELECT ?::uuid, id, now() FROM t_role WHERE code = ?""", serviceAccount, role(r));
        }

        String serviceToken = api.serviceToken(client);
        Map<String, Object> body = fetch(serviceToken);

        assertThat(list(body, "roles")).containsExactlyInAnyOrder(role("R1"), role("R2"));
        assertThat(list(body, "departments"))
                .containsExactlyInAnyOrder(dept("D1"), dept("D1C"), dept("D1CC"), dept("D2"));
        String set = "permissions:user:" + serviceAccount + ":app:" + app;
        assertThat(redis.opsForSet().members(set + ":roles")).containsExactlyInAnyOrder(role("R1"), role("R2"));
        assertThat(redis.opsForSet().members(set + ":departments")).contains(dept("D1CC"), dept("D2"));

        // A department under one of its roles' departments: its subtree grew.
        department("D2C", "D2");

        assertThat(redis.hasKey(set + ":departments")).as("the service account is evicted too").isFalse();
        assertThat(list(fetch(serviceToken), "departments")).contains(dept("D2C"));
    }

    @Test
    @DisplayName("Deleting a department below the user's deletes their sets: the subtree shrank")
    void deletedChildDepartmentEvicts() {
        assign("D1", "R1");
        assertThat(list(fetch(token()), "departments")).contains(dept("D1CC"));

        ok(api.delete("/api/departments/" + dept("D1CC"), admin));

        assertThat(redis.hasKey(setKey() + ":departments")).isFalse();
        assertThat(list(fetch(token()), "departments")).doesNotContain(dept("D1CC"));
    }

    @Test
    @DisplayName("A later write with no roles leaves no roles or departments set behind")
    void emptiedListsAreDeleted() {
        assign("D1", "R1");
        fetch(token());
        assertThat(redis.hasKey(setKey() + ":roles")).isTrue();
        // Two valid roles and no choice: the next write has no active role.
        jdbc.update("""
                INSERT INTO t_user_role_assignment (user_id, role_id, assigned_at)
                SELECT ?, id, now() FROM t_role WHERE code = ?""", userId, role("R2"));
        redis.delete(setKey());

        assertThat(list(fetch(token()), "roles")).isEmpty();
        assertThat(redis.hasKey(setKey() + ":roles")).isFalse();
        assertThat(redis.hasKey(setKey() + ":departments")).isFalse();
    }

    @Test
    @DisplayName("A role change deletes the roles and departments sets with the permission set")
    void roleChangeEvictsThem() {
        assign("D1", "R1");
        fetch(token());
        assertThat(redis.hasKey(setKey() + ":roles")).isTrue();

        assign("D2", "R2");

        assertThat(redis.hasKey(setKey())).isFalse();
        assertThat(redis.hasKey(setKey() + ":roles")).isFalse();
        assertThat(redis.hasKey(setKey() + ":departments")).isFalse();
    }

    @Test
    @DisplayName("A new department under the user's subtree deletes their sets: the subtree grew")
    void newChildDepartmentEvicts() {
        assign("D1", "R1");
        fetch(token());
        assertThat(redis.hasKey(setKey() + ":departments")).isTrue();

        department("D1C2", "D1C");

        assertThat(redis.hasKey(setKey() + ":departments")).isFalse();
        assertThat(list(fetch(token()), "departments")).contains(dept("D1C2"));
    }

    @Test
    @DisplayName("Root: its role code, and no departments: its scope is all, nothing is filtered")
    void root() {
        Map<String, Object> body = fetch(admin);

        assertThat(list(body, "roles")).containsExactly("DEPT_IGRP.superadmin");
        assertThat(list(body, "departments")).isEmpty();
        assertThat(CompatSupport.jsonObject(api.get(FETCH + app, admin).body()).get("scopes").toString())
                .doesNotContain("department", "own", "by_role");
    }

    // ---- fixtures ----

    private void department(String name, String parent, String... roles) {
        ok(api.postJson("/api/departments", admin, "{\"code\":" + CompatSupport.q(dept(name))
                + ",\"name\":\"Dept " + name + "\",\"status\":\"ACTIVE\""
                + (parent == null ? "" : ",\"parentCode\":" + CompatSupport.q(dept(parent))) + "}"));
        ok(api.postJson("/api/departments/" + dept(name) + "/applications", admin, "[" + CompatSupport.q(app) + "]"));
        for (String r : roles) {
            ok(api.postJson("/api/departments/" + dept(name) + "/roles", admin,
                    "{\"code\":" + CompatSupport.q(role(r)) + ",\"name\":\"Role " + r + "\",\"status\":\"ACTIVE\"}"));
        }
    }

    private void assign(String department, String r) {
        ok(api.postJson("/api/users/" + userId + "/departments/" + dept(department) + "/roles", admin,
                "{\"roles\":[" + CompatSupport.q(role(r)) + "]}"));
    }

    /** A new token each time: a role change revokes the user's 0.2 sessions. */
    private String token() {
        return api.userToken(userId, email);
    }

    private Map<String, Object> fetch(String bearer) {
        HttpResponse<String> response = api.get(FETCH + app, bearer);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return CompatSupport.jsonObject(response.body());
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> body, String field) {
        return (List<String>) body.get(field);
    }

    private String dept(String name) {
        return "SD_" + name + "_" + suffix;
    }

    private String role(String name) {
        return "SD_" + name + "_" + suffix;
    }

    private String setKey() {
        return "permissions:user:" + userId + ":app:" + app;
    }

    private static void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }
}
