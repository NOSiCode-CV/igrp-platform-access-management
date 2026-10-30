package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SID hot-path cache must never keep a dead session alive.
 *
 * <p>The cache could not be written on Jackson 2, so it only went live with the
 * Boot 4 migration. {@code SessionEnforcementFilter} trusts it for up to 60
 * seconds, so every revocation has to reach it: the single kill, which saves the
 * entity, and the bulk ones, which update rows with JPQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionCacheRevocationIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    CompatSupport api;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        api.ensureBusinessData();
    }

    /** A live token whose session is confirmed to be in the cache. */
    private String cachedSessionToken() {
        String token = api.superadminToken();
        assertEquals(200, api.get("/api/users/me", token).statusCode());
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(sidKey(token))), "the SID cache was not populated");
        return token;
    }

    private static String sid(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return (String) CompatSupport.jsonObject(payload).get("sid");
    }

    private static String sidKey(String token) {
        return SessionHeartbeatService.SID_KEY_PREFIX + sid(token);
    }

    @Test
    @DisplayName("Killing one session takes effect on the next request")
    void singleKill() {
        String victim = cachedSessionToken();

        HttpResponse<String> kill = api.postJson("/api/admin/sessions/" + sid(victim) + "/kill", api.superadminToken(),
                "{\"reason\":\"compat test\"}");
        assertTrue(kill.statusCode() / 100 == 2, kill.statusCode() + " " + kill.body());

        assertEquals(401, api.get("/api/users/me", victim).statusCode());
    }

    @Test
    @DisplayName("Killing all sessions of a role takes effect on the next request")
    void bulkKillByRole() {
        String victim = cachedSessionToken();

        HttpResponse<String> kill = api.postJson(
                "/api/admin/sessions/roles/" + CompatSupport.BUSINESS_ROLE + "/kill?departmentCode=" + CompatSupport.BUSINESS_DEPARTMENT,
                api.superadminToken(), "{\"reason\":\"compat test\"}");
        assertTrue(kill.statusCode() / 100 == 2, kill.statusCode() + " " + kill.body());

        assertEquals(401, api.get("/api/users/me", victim).statusCode());
    }
}
