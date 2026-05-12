package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase G1 / FR-13 — Layer 3 guard. Asserts the SpEL bean used at the class
 * level on {@code AdminUserSessionController} only admits user-shaped JWTs
 * (those carrying a {@code sid} claim).
 */
class SubjectGuardTest {

    private final SubjectGuard guard = new SubjectGuard();

    @Test
    void nullAuthenticationIsNotAUser() {
        assertFalse(guard.requiresUser(null));
    }

    @Test
    void nonJwtPrincipalIsNotAUser() {
        // M2M chain uses UsernamePasswordAuthenticationToken with a User principal —
        // not a Jwt — and must be rejected by this guard.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "m2m-client", null, List.of());
        assertFalse(guard.requiresUser(auth));
    }

    @Test
    void jwtWithoutSidClaimIsNotAUser() {
        Jwt jwt = jwt(Map.of("sub", "igrp-access-management"));
        assertFalse(guard.requiresUser(new JwtAuthenticationToken(jwt)));
    }

    @Test
    void jwtWithBlankSidClaimIsNotAUser() {
        Jwt jwt = jwt(Map.of("sub", "3", "sid", "   "));
        assertFalse(guard.requiresUser(new JwtAuthenticationToken(jwt)));
    }

    @Test
    void jwtWithValidSidClaimIsAUser() {
        Jwt jwt = jwt(Map.of("sub", "3", "sid", "00000000-0000-0000-0000-000000000001"));
        assertTrue(guard.requiresUser(new JwtAuthenticationToken(jwt)));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Map<String, Object> filled = new HashMap<>(claims);
        if (!filled.containsKey("sub")) {
            filled.put("sub", "3");
        }
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(300),
                Map.of("alg", "RS256"), filled);
    }
}
