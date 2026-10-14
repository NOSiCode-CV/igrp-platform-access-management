package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Several IGRP applications share one browser and one OIDC client, so they all
 * derive the same {@code device_id}. Opening a second app must not close the
 * first app's session.
 *
 * <p>Regression guard for the 2026-10-13 incident on {@code demoigrp.nosi.cv}:
 * the old "one ACTIVE session per (user, device)" rule closed app-center's
 * session when demo authorized, app-center's next call 401'd, and the resulting
 * re-login closed demo's session in turn.
 */
class SessionMultiAppIssuanceTest {

    private static final String USER = "00000000-0000-0000-0000-000000000042";
    private static final String CLIENT = "igrp-access-management";

    private SessionRepository repository;
    private SessionAuditLogger auditLogger;
    private SessionMetrics metrics;
    private SessionIssuanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRepository.class);
        auditLogger = mock(SessionAuditLogger.class);
        metrics = mock(SessionMetrics.class);
        service = new SessionIssuanceService(
                repository,
                mock(SessionCacheEvictService.class),
                new SessionProperties(),
                metrics,
                auditLogger);
        when(repository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SessionEntity activeSession(String deviceId) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(USER);
        entity.setDeviceId(deviceId);
        entity.setClientId(CLIENT);
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setLastSeenAt(Instant.now());
        return entity;
    }

    private SessionEntity openSession(String deviceId, HttpServletRequest request) {
        return (SessionEntity) ReflectionTestUtils.invokeMethod(
                service, "openNewSession",
                USER, deviceId, CLIENT, UUID.randomUUID().toString(),
                request, Instant.now(), 1800L, null);
    }

    // ─── the regression ─────────────────────────────────────────────────

    @Test
    @DisplayName("a second app on the same device does not close the first app's session")
    void secondApp_doesNotCloseTheFirstSession() {
        SessionEntity appCenter = activeSession("derived:same-browser");
        when(repository.findByUserIdAndStatusOrderByLastSeenAtAsc(USER, SessionStatus.ACTIVE))
                .thenReturn(List.of(appCenter));

        SessionEntity demo = openSession("derived:same-browser", new MockHttpServletRequest());

        assertEquals(SessionStatus.ACTIVE, appCenter.getStatus(),
                "app-center's session must survive demo authorizing on the same device");
        assertNull(appCenter.getClosedReason());
        assertEquals(SessionStatus.ACTIVE, demo.getStatus());
        assertNotEquals(appCenter.getSessionId(), demo.getSessionId());
    }

    @Test
    @DisplayName("no SESSION_REPLACED is audited when another app signs in")
    void secondApp_doesNotAuditAReplacement() {
        when(repository.findByUserIdAndStatusOrderByLastSeenAtAsc(USER, SessionStatus.ACTIVE))
                .thenReturn(List.of(activeSession("derived:same-browser")));

        openSession("derived:same-browser", new MockHttpServletRequest());

        verify(auditLogger, never()).recordReplaced(any(), anyString(), anyString(), anyString(), anyString());
        verify(auditLogger).recordCreated(any(), anyString(), anyString(), anyString(), anyString());
    }

    // ─── the premise: apps cannot be told apart by device_id ────────────

    @Test
    @DisplayName("two apps in one browser derive the same device_id")
    void sameBrowserDifferentApps_deriveTheSameDeviceId() {
        // This is why the old rule misfired: device_id hashes
        // (User-Agent, IP, client_id) — none of which differ between two apps
        // on the same domain using the same OIDC client.
        MockHttpServletRequest appCenter = new MockHttpServletRequest();
        appCenter.addHeader("User-Agent", "Mozilla/5.0 Chrome/140");
        appCenter.setRemoteAddr("10.1.2.3");

        MockHttpServletRequest demo = new MockHttpServletRequest();
        demo.addHeader("User-Agent", "Mozilla/5.0 Chrome/140");
        demo.setRemoteAddr("10.1.2.3");

        assertEquals(service.resolveDeviceId(appCenter, CLIENT),
                service.resolveDeviceId(demo, CLIENT));
    }

    @Test
    @DisplayName("an explicit X-Device-Id still wins over the derived hash")
    void suppliedDeviceIdWins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Id", "browser-abc");
        assertEquals("browser-abc", service.resolveDeviceId(request, CLIENT));
    }

    // ─── the cap is now the only bound ──────────────────────────────────

    @Test
    @DisplayName("the per-user cap still evicts least-recently-seen sessions")
    void capStillEvictsLru() {
        // Default cap is 5. Five already open means the oldest is evicted to
        // make room — this is now the ONLY limit on sessions per user, so it
        // must keep working.
        SessionEntity oldest = activeSession("derived:device-a");
        when(repository.findByUserIdAndStatusOrderByLastSeenAtAsc(USER, SessionStatus.ACTIVE))
                .thenReturn(List.of(oldest, activeSession("derived:device-a"), activeSession("derived:device-b"),
                        activeSession("derived:device-b"), activeSession("derived:device-c")));

        openSession("derived:device-c", new MockHttpServletRequest());

        assertEquals(SessionStatus.CLOSED, oldest.getStatus());
        assertEquals("SESSION_LIMIT_EXCEEDED", oldest.getClosedReason());
        verify(metrics).recordEvictedLru();
    }
}
