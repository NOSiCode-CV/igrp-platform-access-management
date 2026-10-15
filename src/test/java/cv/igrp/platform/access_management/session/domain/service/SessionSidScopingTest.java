package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code /api/session/refresh} and {@code /api/session/rotate} must act on the
 * CALLER's session, identified by the {@code sid} claim.
 *
 * <p>Both used to resolve "the user's most recently seen active session". With
 * several applications sharing one browser that is frequently a sibling's row,
 * so one app refreshing slid another app's deadline, and one app rotating
 * closed two sibling sessions and opened a row bound to no token.
 */
class SessionSidScopingTest {

    private static final String USER = "00000000-0000-0000-0000-000000000042";
    private static final String OTHER_USER = "00000000-0000-0000-0000-000000000099";

    private SessionRepository repository;
    private SessionManagementService service;

    private SessionEntity appCenter;
    private SessionEntity demo;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRepository.class);
        IGRPUserEntityRepository userRepository = mock(IGRPUserEntityRepository.class);
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        service = new SessionManagementService(
                repository,
                userRepository,
                mock(RoleMapper.class),
                mock(SessionCacheService.class),
                mock(SessionCacheEvictService.class),
                new SessionProperties());

        when(repository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // demo is the most recently seen; app-center is the caller below.
        appCenter = session(USER, Instant.now().minus(10, ChronoUnit.MINUTES));
        demo = session(USER, Instant.now());
        when(repository.findByUserIdAndStatusOrderByLastSeenAtDesc(USER, SessionStatus.ACTIVE))
                .thenReturn(List.of(demo, appCenter));
        when(repository.findBySessionId(appCenter.getSessionId())).thenReturn(Optional.of(appCenter));
        when(repository.findBySessionId(demo.getSessionId())).thenReturn(Optional.of(demo));
    }

    private SessionEntity session(String userId, Instant lastSeen) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setDeviceId("derived:same-browser");
        entity.setClientId("igrp-access-management");
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setStartedAt(lastSeen);
        entity.setLastSeenAt(lastSeen);
        entity.setExpiresAt(Instant.now().plus(20, ChronoUnit.MINUTES));
        entity.setAbsoluteExpiresAt(Instant.now().plus(8, ChronoUnit.HOURS));
        return entity;
    }

    // Rotation used to live here. It was removed in favour of a 501 on the
    // endpoint: closing the caller's row and opening a new one cannot work over
    // REST, because nothing hands the caller the new sid. See
    // SessionRotateNotImplementedTest and SessionManagementService's note.

    // ─── refresh ────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh extends the caller's session, not the most recent one")
    void refresh_extendsTheCallersSession() {
        Instant siblingDeadline = demo.getExpiresAt();

        var result = service.refreshSession(USER, appCenter.getSessionId(), 3600);

        assertTrue(result.isPresent());
        assertTrue(appCenter.getExpiresAt().isAfter(Instant.now().plus(50, ChronoUnit.MINUTES)),
                "the caller's deadline should have moved");
        assertEquals(siblingDeadline, demo.getExpiresAt(), "the sibling's deadline must not move");
    }

    @Test
    @DisplayName("refresh refuses a sid belonging to another subject")
    void refresh_refusesForeignSid() {
        SessionEntity foreign = session(OTHER_USER, Instant.now());
        Instant before = foreign.getExpiresAt();
        when(repository.findBySessionId(foreign.getSessionId())).thenReturn(Optional.of(foreign));

        assertTrue(service.refreshSession(USER, foreign.getSessionId(), 3600).isEmpty());
        assertEquals(before, foreign.getExpiresAt());
    }

    @Test
    @DisplayName("refresh refuses a session that is no longer active")
    void refresh_refusesClosedSession() {
        appCenter.close("USER_LOGOUT", "USER");

        assertTrue(service.refreshSession(USER, appCenter.getSessionId(), 3600).isEmpty(),
                "a closed session must not be refreshed back into service");
    }

    @Test
    @DisplayName("without a sid it still falls back, for tokens issued before sid existed")
    void refresh_fallsBackWhenSidAbsent() {
        assertTrue(service.refreshSession(USER, null, 3600).isPresent(),
                "legacy tokens must keep working");
    }
}
