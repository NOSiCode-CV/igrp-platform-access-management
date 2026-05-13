package cv.igrp.platform.access_management.session.infrastructure.audit;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * T-NF4 — exhaustive coverage that every session-lifecycle transition
 * results in exactly one {@code SecurityAuditService.logEvent} invocation
 * with the expected {@code eventType}, {@code category=AUTHENTICATION},
 * {@code reason} and {@code actor}.
 *
 * <p>The transition sites themselves call into {@link SessionAuditLogger},
 * so the helper is the single fan-in. Verifying its contract here covers
 * every wired call site by construction (each site's test asserts that the
 * helper method is invoked exactly once with the right arguments — see
 * sibling tests in the {@code oauth_server} and {@code session} packages).
 */
@ExtendWith(MockitoExtension.class)
class AuditTrailCoverageTest {

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private SessionAuditLogger logger;

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> ctxCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private void assertSingleRow(AuditEventType expectedType,
                                 String expectedReason,
                                 String expectedActorPattern) {
        ArgumentCaptor<Map<String, Object>> captor = ctxCaptor();
        verify(securityAuditService, times(1))
                .logEvent(eq(expectedType), eq(AuditCategory.AUTHENTICATION), captor.capture());
        Map<String, Object> ctx = captor.getValue();
        assertThat(ctx).containsEntry("reason", expectedReason);
        assertThat(ctx.get("actor"))
                .asString()
                .matches(expectedActorPattern);
        verifyNoMoreInteractions(securityAuditService);
    }

    @Test
    void sessionCreated_emitsOneRow_userActor() {
        logger.recordCreated(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "device-1", "web", SessionAuditLogger.USER);
        assertSingleRow(AuditEventType.SESSION_CREATED, "NEW_LOGIN", "USER");
    }

    @Test
    void sessionReplaced_emitsOneRow_userActor() {
        logger.recordReplaced(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "device-1", "web", SessionAuditLogger.USER);
        assertSingleRow(AuditEventType.SESSION_REPLACED, "SESSION_REPLACED", "USER");
    }

    @Test
    void sessionLimitExceeded_emitsOneRow_systemActor() {
        logger.recordLimitExceeded(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "device-1", "web");
        assertSingleRow(AuditEventType.SESSION_LIMIT_EXCEEDED, "SESSION_LIMIT_EXCEEDED", "SYSTEM");
    }

    @Test
    void sessionRefreshed_emitsOneRow_userActor() {
        logger.recordRefreshed(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "device-1", "web", SessionAuditLogger.USER);
        assertSingleRow(AuditEventType.SESSION_REFRESHED, "TOKEN_REFRESH", "USER");
    }

    @Test
    void sessionRevoked_refreshReuse_emitsOneRow_systemActor() {
        logger.recordRevoked(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "REFRESH_TOKEN_REUSE", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "REFRESH_TOKEN_REUSE", "SYSTEM");
    }

    @Test
    void sessionRevoked_userLogout_emitsOneRow_userActor() {
        logger.recordRevoked(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "USER_LOGOUT", SessionAuditLogger.USER);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "USER_LOGOUT", "USER");
    }

    @Test
    void sessionRevoked_oauthAuthorizationRemoved_emitsOneRow_systemActor() {
        logger.recordRevoked(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "OAUTH_AUTHORIZATION_REMOVED",
                SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "OAUTH_AUTHORIZATION_REMOVED", "SYSTEM");
    }

    @Test
    void sessionRevoked_userRoleChanged_emitsOneRow_systemActor() {
        logger.recordRevoked(null, "00000000-0000-0000-0000-000000000042", "USER_ROLE_CHANGED", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "USER_ROLE_CHANGED", "SYSTEM");
    }

    @Test
    void sessionRevoked_rolePermissionsChanged_emitsOneRow_systemActor() {
        logger.recordRevoked(null, null, "ROLE_PERMISSIONS_CHANGED", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "ROLE_PERMISSIONS_CHANGED", "SYSTEM");
    }

    @Test
    void sessionRevoked_userStatusChanged_emitsOneRow_systemActor() {
        logger.recordRevoked(null, "00000000-0000-0000-0000-000000000042", "USER_STATUS_CHANGED", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "USER_STATUS_CHANGED", "SYSTEM");
    }

    @Test
    void sessionRevoked_departmentScopeChanged_emitsOneRow_systemActor() {
        logger.recordRevoked(null, null, "DEPARTMENT_SCOPE_CHANGED", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "DEPARTMENT_SCOPE_CHANGED", "SYSTEM");
    }

    @Test
    void sessionRevoked_permissionDeleted_emitsOneRow_systemActor() {
        logger.recordRevoked(null, "00000000-0000-0000-0000-000000000042", "PERMISSION_DELETED", SessionAuditLogger.SYSTEM);
        assertSingleRow(AuditEventType.SESSION_REVOKED, "PERMISSION_DELETED", "SYSTEM");
    }

    @Test
    void sessionRevoked_adminKill_emitsOneRow_adminActorPattern() {
        logger.recordRevoked(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "ADMIN_KILL",
                SessionAuditLogger.adminActor("admin-ext-7"));
        assertSingleRow(AuditEventType.SESSION_REVOKED, "ADMIN_KILL", "ADMIN:.+");
    }

    @Test
    void sessionRevoked_adminLogoutAll_emitsOneRow_adminActorPattern() {
        logger.recordRevoked(null, "00000000-0000-0000-0000-000000000042", "ADMIN_LOGOUT_ALL",
                SessionAuditLogger.adminActor("admin-ext-9"));
        assertSingleRow(AuditEventType.SESSION_REVOKED, "ADMIN_LOGOUT_ALL", "ADMIN:.+");
    }

    @Test
    void sessionExpired_idleTimeout_emitsOneRow_systemActor() {
        logger.recordExpired(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "IDLE_TIMEOUT");
        assertSingleRow(AuditEventType.SESSION_EXPIRED, "IDLE_TIMEOUT", "SYSTEM");
    }

    @Test
    void sessionExpired_absoluteTimeout_emitsOneRow_systemActor() {
        logger.recordExpired(UUID.randomUUID(), "00000000-0000-0000-0000-000000000042", "ABSOLUTE_TIMEOUT");
        assertSingleRow(AuditEventType.SESSION_EXPIRED, "ABSOLUTE_TIMEOUT", "SYSTEM");
    }

    @Test
    void sessionForcedReauth_emitsOneRow_adminActorPattern() {
        logger.recordForcedReauth("00000000-0000-0000-0000-000000000042", SessionAuditLogger.adminActor("admin-ext-3"));
        assertSingleRow(AuditEventType.SESSION_FORCED_REAUTH, "FORCED_REAUTH", "ADMIN:.+");
    }

    @Test
    void contextCarriesSidSubDeviceClient() {
        UUID sid = UUID.randomUUID();
        logger.recordCreated(sid, "00000000-0000-0000-0000-000000000099", "dev-X", "web-client", SessionAuditLogger.USER);

        ArgumentCaptor<Map<String, Object>> captor = ctxCaptor();
        verify(securityAuditService)
                .logEvent(eq(AuditEventType.SESSION_CREATED), eq(AuditCategory.AUTHENTICATION),
                        captor.capture());
        Map<String, Object> ctx = captor.getValue();
        assertThat(ctx).containsEntry("sid", sid.toString());
        assertThat(ctx).containsEntry("sessionId", sid.toString());
        assertThat(ctx).containsEntry("sub", "00000000-0000-0000-0000-000000000099");
        assertThat(ctx).containsEntry("userId", "00000000-0000-0000-0000-000000000099");
        assertThat(ctx).containsEntry("deviceId", "dev-X");
        assertThat(ctx).containsEntry("clientId", "web-client");
    }

    @Test
    void emitFailureIsSwallowed_neverThrows() {
        org.mockito.Mockito.doThrow(new RuntimeException("audit DB down"))
                .when(securityAuditService).logEvent(any(), any(), any());
        // Must not propagate — the transition must continue.
        logger.recordCreated(UUID.randomUUID(), "00000000-0000-0000-0000-000000000001", "d", "c", SessionAuditLogger.USER);
    }
}
