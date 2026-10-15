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
 * {@code POST /api/session/close} ends the caller's own session, server-side.
 *
 * <p>The Studio metadata had declared this action for some time, which is why
 * the frontend's generated SessionClient carried a close() method against an
 * endpoint that did not exist. The contract here matches that declaration: no
 * request body, 204 when a session was closed, 404 when the caller had none.
 */
class SessionCloseTest {

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
        return entity;
    }

    @Test
    @DisplayName("closes the caller's session and leaves siblings alone")
    void closesOnlyTheCallersSession() {
        assertTrue(service.closeCallerSession(USER, appCenter.getSessionId(), "USER_CLOSED"));

        assertEquals(SessionStatus.CLOSED, appCenter.getStatus());
        assertEquals("USER_CLOSED", appCenter.getClosedReason());
        assertEquals(SessionStatus.ACTIVE, demo.getStatus(), "a sibling application must keep its session");
    }

    @Test
    @DisplayName("reports false when the session is already closed, so the endpoint 404s")
    void alreadyClosed_reportsFalse() {
        appCenter.close("USER_LOGOUT", "USER");

        assertFalse(service.closeCallerSession(USER, appCenter.getSessionId(), "USER_CLOSED"));
    }

    @Test
    @DisplayName("reports false when there is no such session")
    void unknownSession_reportsFalse() {
        UUID unknown = UUID.randomUUID();
        when(repository.findBySessionId(unknown)).thenReturn(Optional.empty());

        assertFalse(service.closeCallerSession(USER, unknown, "USER_CLOSED"));
    }

    @Test
    @DisplayName("refuses a sid belonging to another subject")
    void refusesForeignSid() {
        SessionEntity foreign = session(OTHER_USER, Instant.now());
        when(repository.findBySessionId(foreign.getSessionId())).thenReturn(Optional.of(foreign));

        assertFalse(service.closeCallerSession(USER, foreign.getSessionId(), "USER_CLOSED"));
        assertEquals(SessionStatus.ACTIVE, foreign.getStatus());
    }

    @Test
    @DisplayName("without a sid it falls back, for tokens issued before sid existed")
    void noSid_fallsBack() {
        assertTrue(service.closeCallerSession(USER, null, "USER_CLOSED"));

        assertEquals(SessionStatus.CLOSED, demo.getStatus(), "the fallback picks the most recently seen");
    }
}
