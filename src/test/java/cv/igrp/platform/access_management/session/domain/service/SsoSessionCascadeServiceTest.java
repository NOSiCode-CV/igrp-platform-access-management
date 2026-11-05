package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2.1–R2.4 — revoking the sibling sessions of one AM sign-in.
 */
@ExtendWith(MockitoExtension.class)
class SsoSessionCascadeServiceTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";
    private static final String KEY = "a-hashed-sign-in-key";

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionCacheEvictService sessionCacheEvictService;
    @Mock
    private SessionHeartbeatService heartbeatService;
    @Mock
    private SessionAuditLogger sessionAuditLogger;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SsoSessionCascadeService service;

    private SessionEntity session(UUID sid) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(sid);
        entity.setUserId(USER_ID);
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setSsoSessionKey(KEY);
        entity.setStartedAt(Instant.now().minusSeconds(60));
        entity.setLastSeenAt(Instant.now());
        return entity;
    }

    @Test
    @DisplayName("Revokes every sibling as USER_LOGOUT and leaves the bound row alone")
    void revokesSiblingsExcludingTheBoundRow() {
        UUID bound = UUID.randomUUID();
        UUID siblingA = UUID.randomUUID();
        UUID siblingB = UUID.randomUUID();
        when(sessionRepository.findByUserIdAndSsoSessionKeyAndStatus(USER_ID, KEY, SessionStatus.ACTIVE))
                .thenReturn(List.of(session(bound), session(siblingA), session(siblingB)));

        int revoked = service.revokeSiblings(USER_ID, KEY, bound);

        assertEquals(2, revoked, "the bound row is already revoked and must not be counted again");

        ArgumentCaptor<SessionEntity> saved = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository, times(2)).save(saved.capture());
        assertTrue(saved.getAllValues().stream().noneMatch(s -> bound.equals(s.getSessionId())),
                "the bound row must not be revoked twice — it would duplicate its audit entry and event");
        saved.getAllValues().forEach(s -> {
            assertEquals(SessionStatus.REVOKED, s.getStatus());
            // Must be distinguishable from SESSION_LIMIT_EXCEEDED: a cap
            // eviction is not a sign-out, and applications are expected to tell
            // them apart (V7).
            assertEquals("USER_LOGOUT", s.getClosedReason());
            assertEquals("USER", s.getClosedBy());
        });

        verify(heartbeatService).evict(siblingA);
        verify(heartbeatService).evict(siblingB);
        verify(heartbeatService, never()).evict(bound);
        verify(eventPublisher, times(2)).publishEvent(any(SessionRevokedEvent.class));
        verify(sessionAuditLogger, times(2))
                .recordRevoked(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Evicts the subject cache once, not once per application")
    void evictsSubjectCacheOnce() {
        when(sessionRepository.findByUserIdAndSsoSessionKeyAndStatus(USER_ID, KEY, SessionStatus.ACTIVE))
                .thenReturn(List.of(session(UUID.randomUUID()), session(UUID.randomUUID()),
                        session(UUID.randomUUID())));

        service.revokeSiblings(USER_ID, KEY, UUID.randomUUID());

        // The cache is keyed by subject, so a per-row eviction would repeat
        // identical work for every application the user had open.
        verify(sessionCacheEvictService, times(1)).evictBySubject(USER_ID);
    }

    @Test
    @DisplayName("A null or blank key cascades to nothing and touches no row")
    void nullKeyIsANoOp() {
        // Critical: a null key must NOT reach the query. Rows predating R1.1 all
        // have a null key, so matching on it would revoke every keyless session
        // the user owns — including ones from entirely different browsers, which
        // D1 rules out.
        assertEquals(0, service.revokeSiblings(USER_ID, null, UUID.randomUUID()));
        assertEquals(0, service.revokeSiblings(USER_ID, "   ", UUID.randomUUID()));
        assertEquals(0, service.revokeSiblings(null, KEY, UUID.randomUUID()));

        verify(sessionRepository, never())
                .findByUserIdAndSsoSessionKeyAndStatus(any(), any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("No siblings is a quiet no-op, not an error")
    void noSiblingsIsFine() {
        UUID bound = UUID.randomUUID();
        when(sessionRepository.findByUserIdAndSsoSessionKeyAndStatus(USER_ID, KEY, SessionStatus.ACTIVE))
                .thenReturn(List.of(session(bound)));

        assertEquals(0, service.revokeSiblings(USER_ID, KEY, bound));

        verify(sessionRepository, never()).save(any());
        verify(sessionCacheEvictService, never()).evictBySubject(any());
    }
}
