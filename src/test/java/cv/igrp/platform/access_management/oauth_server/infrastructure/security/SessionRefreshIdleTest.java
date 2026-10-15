package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Refresh must honour the idle deadline, not only the absolute ceiling.
 *
 * <p>Before this, an idle-expired session was rejected by
 * {@code SessionEnforcementFilter} with {@code session_expired} while the token
 * endpoint happily refreshed it and slid its deadline forward — the row stayed
 * alive until {@code SessionCleanupScheduler}'s next sweep happened to flip it.
 * A session must not be dead to the API and refreshable at the same moment.
 */
class SessionRefreshIdleTest {

    private SessionRepository repository;
    private SessionMetrics metrics;
    private SessionIssuanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRepository.class);
        metrics = mock(SessionMetrics.class);
        service = new SessionIssuanceService(
                repository,
                mock(SessionCacheEvictService.class),
                new SessionProperties(),
                metrics,
                mock(SessionAuditLogger.class));
        when(repository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SessionEntity session(Instant expiresAt, Instant absoluteExpiresAt) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId("00000000-0000-0000-0000-000000000042");
        entity.setDeviceId("derived:browser");
        entity.setClientId("igrp-access-management");
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setExpiresAt(expiresAt);
        entity.setAbsoluteExpiresAt(absoluteExpiresAt);
        return entity;
    }

    private SessionEntity refresh(SessionEntity entity, Instant now) {
        return (SessionEntity) ReflectionTestUtils.invokeMethod(
                service, "applyRefresh", entity, "jti-1", "igrp-access-management", now, 1800L, null);
    }

    @Test
    @DisplayName("an idle-expired session cannot be refreshed back to life")
    void idleExpired_isRejected() {
        Instant now = Instant.now();
        SessionEntity entity = session(now.minus(1, ChronoUnit.MINUTES), now.plus(4, ChronoUnit.HOURS));

        var ex = assertThrows(SessionIssuanceService.SessionRefreshRejectedException.class,
                () -> refresh(entity, now));

        assertTrue(ex.getMessage().contains("idle deadline"), ex.getMessage());
        assertEquals(SessionStatus.EXPIRED, entity.getStatus(),
                "the row should be settled, not left ACTIVE for the next sweep to find");
        verify(metrics).recordRefreshRejected("idle_timeout_exceeded");
    }

    @Test
    @DisplayName("a session inside its idle window still refreshes and slides forward")
    void withinIdleWindow_slides() {
        Instant now = Instant.now();
        Instant deadline = now.plus(10, ChronoUnit.MINUTES);
        SessionEntity entity = session(deadline, now.plus(4, ChronoUnit.HOURS));

        SessionEntity refreshed = refresh(entity, now);

        assertEquals(SessionStatus.ACTIVE, refreshed.getStatus());
        assertTrue(refreshed.getExpiresAt().isAfter(deadline), "deadline should have slid forward");
        verify(metrics, never()).recordRefreshRejected(any());
    }

    @Test
    @DisplayName("the absolute ceiling still wins over a healthy idle deadline")
    void absoluteCeiling_stillEnforced() {
        Instant now = Instant.now();
        SessionEntity entity = session(now.plus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));

        var ex = assertThrows(SessionIssuanceService.SessionRefreshRejectedException.class,
                () -> refresh(entity, now));

        assertTrue(ex.getMessage().contains("absolute lifetime"), ex.getMessage());
        verify(metrics).recordRefreshRejected("absolute_lifetime_exceeded");
    }

    @Test
    @DisplayName("a session with no idle deadline set is not rejected by the new check")
    void nullIdleDeadline_isTolerated() {
        Instant now = Instant.now();
        SessionEntity refreshed = refresh(session(null, now.plus(4, ChronoUnit.HOURS)), now);

        assertEquals(SessionStatus.ACTIVE, refreshed.getStatus());
    }
}
