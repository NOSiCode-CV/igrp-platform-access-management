package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** The access audit rows: a logout, a kill (#33) and a user-status change, never failing their action. */
@ExtendWith(MockitoExtension.class)
class AccessAuditLoggerTest {

    @Mock
    private SecurityAuditService audit;

    @SuppressWarnings("unchecked")
    private Map<String, Object> recorded(AuditEventType type, AuditCategory category) {
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(audit).logEvent(eq(type), eq(category), context.capture());
        return context.getValue();
    }

    @Test
    void logout() {
        new AccessAuditLogger(audit).recordLogout("u1");

        assertThat(recorded(AuditEventType.LOGOUT, AuditCategory.AUTHENTICATION))
                .containsEntry("reason", "USER_LOGOUT").containsEntry("actor", AccessAuditLogger.USER)
                .containsEntry("userId", "u1").containsEntry("sub", "u1");
    }

    @Test
    void accessEnded() {
        new AccessAuditLogger(audit).recordAccessEnded("u1", "ADMIN_KILL", AccessAuditLogger.adminActor("maria"));

        assertThat(recorded(AuditEventType.TOKEN_REVOKED, AuditCategory.AUTHENTICATION))
                .containsEntry("reason", "ADMIN_KILL").containsEntry("actor", "ADMIN:maria").containsEntry("userId", "u1");
    }

    @Test
    void accessEndedDefaults() {
        new AccessAuditLogger(audit).recordAccessEnded(null, null, null);

        assertThat(recorded(AuditEventType.TOKEN_REVOKED, AuditCategory.AUTHENTICATION))
                .containsEntry("reason", "UNSPECIFIED").containsEntry("actor", AccessAuditLogger.SYSTEM)
                .doesNotContainKey("userId");
    }

    @Test
    void userStatusTransitioned() {
        new AccessAuditLogger(audit).recordUserStatusTransitioned("u1", "TEMPORARY", "ACTIVE", null, null);

        assertThat(recorded(AuditEventType.USER_UPDATED, AuditCategory.USER_MANAGEMENT))
                .containsEntry("reason", "USER_STATUS_TRANSITIONED").containsEntry("actor", AccessAuditLogger.SYSTEM)
                .containsEntry("from", "TEMPORARY").containsEntry("to", "ACTIVE");
    }

    @Test
    void unknownStatusesAndAdminWithoutId() {
        new AccessAuditLogger(audit).recordUserStatusTransitioned("u1", null, null,
                AccessAuditLogger.adminActor(null), "ADMIN_DEACTIVATE");

        assertThat(recorded(AuditEventType.USER_UPDATED, AuditCategory.USER_MANAGEMENT))
                .containsEntry("from", "<none>").containsEntry("to", "<none>").containsEntry("actor", "ADMIN:unknown");
    }

    @Test
    void anAuditFailureNeverFailsTheAction() {
        doThrow(new IllegalStateException("audit down")).when(audit).logEvent(any(), any(), any());

        assertThatCode(() -> new AccessAuditLogger(audit).recordLogout("u1")).doesNotThrowAnyException();
    }
}
