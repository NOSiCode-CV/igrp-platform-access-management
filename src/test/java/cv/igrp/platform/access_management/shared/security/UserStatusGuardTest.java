package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase G3 — Asserts the SpEL bean used at {@code @PreAuthorize} sites only
 * admits users in the right lifecycle state. {@code requiresActive} passes
 * only for ACTIVE; {@code requiresActiveOrTemporary} also lets TEMPORARY in.
 */
class UserStatusGuardTest {

    private IGRPUserEntityRepository userRepository;
    private UserStatusGuard guard;

    @BeforeEach
    void setUp() {
        userRepository = mock(IGRPUserEntityRepository.class);
        guard = new UserStatusGuard(userRepository);
    }

    @Test
    void nullAuthenticationDenied() {
        assertFalse(guard.requiresActive(null));
        assertFalse(guard.requiresActiveOrTemporary(null));
    }

    @Test
    void nonJwtPrincipalDenied() {
        Authentication auth = new UsernamePasswordAuthenticationToken("m2m", null, List.of());
        assertFalse(guard.requiresActive(auth));
        assertFalse(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void nonNumericSubDenied() {
        Jwt jwt = jwt(Map.of("sub", "not-a-number"));
        Authentication auth = new JwtAuthenticationToken(jwt);
        assertFalse(guard.requiresActive(auth));
        assertFalse(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void missingUserDenied() {
        String uid = "00000000-0000-0000-0000-000000000007";
        Jwt jwt = jwt(Map.of("sub", uid));
        when(userRepository.findById(eq(uid))).thenReturn(Optional.empty());
        Authentication auth = new JwtAuthenticationToken(jwt);
        assertFalse(guard.requiresActive(auth));
        assertFalse(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void activeUserPermitted() {
        Authentication auth = authForUserWithStatus(7, Status.ACTIVE);
        assertTrue(guard.requiresActive(auth));
        assertTrue(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void temporaryUserOnlyPermittedByLenientGuard() {
        Authentication auth = authForUserWithStatus(8, Status.TEMPORARY);
        assertFalse(guard.requiresActive(auth));
        assertTrue(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void inactiveUserDenied() {
        Authentication auth = authForUserWithStatus(9, Status.INACTIVE);
        assertFalse(guard.requiresActive(auth));
        assertFalse(guard.requiresActiveOrTemporary(auth));
    }

    @Test
    void deletedUserDenied() {
        Authentication auth = authForUserWithStatus(10, Status.DELETED);
        assertFalse(guard.requiresActive(auth));
        assertFalse(guard.requiresActiveOrTemporary(auth));
    }

    private Authentication authForUserWithStatus(int id, Status status) {
        String uid = String.format("00000000-0000-0000-0000-%012d", id);
        Jwt jwt = jwt(Map.of("sub", uid));
        IGRPUserEntity entity = new IGRPUserEntity();
        entity.setId(uid);
        entity.setStatus(status);
        when(userRepository.findById(eq(uid))).thenReturn(Optional.of(entity));
        return new JwtAuthenticationToken(jwt);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Map<String, Object> filled = new HashMap<>(claims);
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(300),
                Map.of("alg", "RS256"), filled);
    }
}
