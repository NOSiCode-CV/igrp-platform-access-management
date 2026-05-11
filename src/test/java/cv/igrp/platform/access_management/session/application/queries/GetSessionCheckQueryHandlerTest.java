package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.platform.access_management.session.application.dto.SessionCheckResponseDTO;
import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetSessionCheckQueryHandler}. Covers the response
 * payload contract for the four session states the FE needs to disambiguate:
 * live, revoked, idle-expired, absolute-expired, plus the no-sid / no-row
 * fallbacks.
 */
@ExtendWith(MockitoExtension.class)
class GetSessionCheckQueryHandlerTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionProperties sessionProperties;

    @InjectMocks
    private GetSessionCheckQueryHandler handler;

    private UUID sid;

    @BeforeEach
    void setUp() {
        sid = UUID.randomUUID();
    }

    @Test
    void valid_whenActiveSessionAndUnexpired() {
        SessionEntity session = newSession(SessionStatus.ACTIVE,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(3600),
                Instant.now().minusSeconds(5));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        when(sessionProperties.getTimeoutSeconds()).thenReturn(1800L);

        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(sid, "42"));

        assertTrue(out.isValid());
        assertEquals(sid, out.getSid());
        assertEquals("42", out.getSub());
        assertNotNull(out.getIdleTimeoutAt());
    }

    @Test
    void invalid_missingSidReturnsReason() {
        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(null, "42"));

        assertFalse(out.isValid());
        assertEquals("MISSING_SID", out.getReason());
    }

    @Test
    void invalid_sessionNotFound() {
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.empty());

        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(sid, "42"));

        assertFalse(out.isValid());
        assertEquals("SESSION_NOT_FOUND", out.getReason());
    }

    @Test
    void invalid_revokedSessionUsesClosedReason() {
        SessionEntity session = newSession(SessionStatus.REVOKED,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(3600),
                Instant.now().minusSeconds(5));
        session.setClosedReason("USER_ROLE_CHANGED");
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        when(sessionProperties.getTimeoutSeconds()).thenReturn(1800L);

        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(sid, "42"));

        assertFalse(out.isValid());
        assertEquals("USER_ROLE_CHANGED", out.getReason());
    }

    @Test
    void invalid_idleTimeout() {
        SessionEntity session = newSession(SessionStatus.ACTIVE,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600),
                Instant.now().minusSeconds(120));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        when(sessionProperties.getTimeoutSeconds()).thenReturn(60L);

        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(sid, "42"));

        assertFalse(out.isValid());
        assertEquals("IDLE_TIMEOUT", out.getReason());
    }

    @Test
    void invalid_absoluteTimeout() {
        SessionEntity session = newSession(SessionStatus.ACTIVE,
                Instant.now().plusSeconds(60),
                Instant.now().minusSeconds(1),
                Instant.now().minusSeconds(5));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        when(sessionProperties.getTimeoutSeconds()).thenReturn(1800L);

        SessionCheckResponseDTO out = handler.handle(new GetSessionCheckQuery(sid, "42"));

        assertFalse(out.isValid());
        assertEquals("ABSOLUTE_TIMEOUT", out.getReason());
    }

    private SessionEntity newSession(SessionStatus status,
                                     Instant expiresAt,
                                     Instant absoluteExpiresAt,
                                     Instant lastSeenAt) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(sid);
        entity.setUserId(42);
        entity.setStatus(status);
        entity.setStartedAt(Instant.now().minusSeconds(600));
        entity.setLastSeenAt(lastSeenAt);
        entity.setExpiresAt(expiresAt);
        entity.setAbsoluteExpiresAt(absoluteExpiresAt);
        return entity;
    }
}
