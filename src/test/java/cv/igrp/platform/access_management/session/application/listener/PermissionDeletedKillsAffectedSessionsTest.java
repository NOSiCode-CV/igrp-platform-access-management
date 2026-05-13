package cv.igrp.platform.access_management.session.application.listener;

import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-D6 (validation.md): {@code PermissionDeletedKillsAffectedSessionsTest}
 * <p>
 * Covers FR-16: deleting a permission held by N users (across different roles)
 * MUST result in exactly those N users having their sessions revoked. The
 * cascade is event-driven, so this test exercises the listener that converts a
 * {@link DeletePermissionEvent} into an {@code invalidateUserSessions} call —
 * the same listener that {@code PermissionSyncService} (the only current
 * publish site, FR-16 / Phase D8) triggers when it soft-deletes a permission
 * absent from an incoming M2M sync payload.
 */
@ExtendWith(MockitoExtension.class)
class PermissionDeletedKillsAffectedSessionsTest {

    @Mock
    private SessionInvalidationService sessionInvalidationService;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private SessionAuditLogger sessionAuditLogger;

    @InjectMocks
    private SessionInvalidationEventListener listener;

    @Test
    @DisplayName("T-D6: kills sessions of every user that held the deleted permission")
    void handlePermissionDeleted_killsAllAffectedUsers() {
        // Given — three users (across different roles) hold "users.read"
        String u1 = "00000000-0000-0000-0000-000000000101";
        String u2 = "00000000-0000-0000-0000-000000000202";
        String u3 = "00000000-0000-0000-0000-000000000303";
        Set<String> affected = Set.of(u1, u2, u3);
        when(userRepository.findUserIdsByPermissionName("users.read")).thenReturn(affected);

        // When — listener receives the cascade event
        listener.handlePermissionDeleted(new DeletePermissionEvent(this, "users.read"));

        // Then — exactly those three users have their sessions revoked with the
        // PERMISSION_DELETED reason, in a single batch call (no overspray, no leaks).
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> userIdsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(sessionInvalidationService)
                .invalidateUserSessions(userIdsCaptor.capture(), eq("PERMISSION_DELETED"));
        assertThat(userIdsCaptor.getValue()).containsExactlyInAnyOrder(u1, u2, u3);

        // And each affected user gets an audit record for the revocation.
        verify(sessionAuditLogger).recordRevoked(eq(null), eq(u1), eq("PERMISSION_DELETED"), eq(SessionAuditLogger.SYSTEM));
        verify(sessionAuditLogger).recordRevoked(eq(null), eq(u2), eq("PERMISSION_DELETED"), eq(SessionAuditLogger.SYSTEM));
        verify(sessionAuditLogger).recordRevoked(eq(null), eq(u3), eq("PERMISSION_DELETED"), eq(SessionAuditLogger.SYSTEM));
    }

    @Test
    @DisplayName("T-D6: no-op when no active user holds the deleted permission")
    void handlePermissionDeleted_noUsersHolding_doesNotInvalidate() {
        when(userRepository.findUserIdsByPermissionName("orphan.permission"))
                .thenReturn(Collections.emptySet());

        listener.handlePermissionDeleted(new DeletePermissionEvent(this, "orphan.permission"));

        verify(sessionInvalidationService, never()).invalidateUserSessions(any(), any());
    }

    @Test
    @DisplayName("T-D6: blank permissionName is skipped without a repository hit")
    void handlePermissionDeleted_blankPermissionName_skipped() {
        listener.handlePermissionDeleted(new DeletePermissionEvent(this, "  "));

        verify(userRepository, never()).findUserIdsByPermissionName(any());
        verify(sessionInvalidationService, never()).invalidateUserSessions(any(), any());
    }
}
