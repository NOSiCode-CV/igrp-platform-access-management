package cv.igrp.platform.access_management.session.application.listener;

import cv.igrp.platform.access_management.session.domain.event.SessionClosedEvent;
import cv.igrp.platform.access_management.session.domain.event.SessionCreatedEvent;
import cv.igrp.platform.access_management.session.domain.event.SessionExpiredEvent;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Diagnostic listener for the session-lifecycle {@code ApplicationEvent}s.
 *
 * <p>NFR-4 audit rows are written at the SOURCE of each transition through
 * {@link cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger}
 * — see {@code SessionIssuanceService}, {@code SessionCleanupScheduler},
 * {@code SessionLogoutHandler}, {@code RevocationCascadeListener},
 * {@code RefreshTokenReuseGuard}, {@code SessionInvalidationEventListener},
 * {@code ForcedReAuthService} and {@code AdminUserSessionController}.
 * Auditing here too would produce duplicate rows for the sites that also
 * publish one of these events, so this listener stays observation-only.
 */
@Slf4j
@Component
public class SessionEventListener {

    private final SessionInvalidationService sessionInvalidationService;

    public SessionEventListener(SessionInvalidationService sessionInvalidationService) {
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @EventListener
    public void handleSessionCreated(SessionCreatedEvent event) {
        log.info("Session created: {} for user: {} from IP: {}",
                event.getSessionId(), event.getUserId(), event.getClientIp());
        // NFR-4 audit emitted at source (SessionIssuanceService).
    }

    @EventListener
    public void handleSessionClosed(SessionClosedEvent event) {
        log.info("Session closed: {} for user: {} with reason: {} by: {}",
                event.getSessionId(), event.getUserId(), event.getReason(), event.getClosedBy());
        // NFR-4 audit emitted at source (SessionIssuanceService replace path, etc.).
    }

    @EventListener
    public void handleSessionExpired(SessionExpiredEvent event) {
        log.info("Session expired: {} for user: {}",
                event.getSessionId(), event.getUserId());
        // NFR-4 audit emitted at source (SessionCleanupScheduler).
    }

    @EventListener
    public void handleSessionRevoked(SessionRevokedEvent event) {
        log.warn("Session revoked: {} for user: {} with reason: {} by: {}",
                event.getSessionId(), event.getUserId(), event.getReason(), event.getRevokedBy());
        // NFR-4 audit emitted at source (SessionLogoutHandler / RefreshTokenReuseGuard /
        // RevocationCascadeListener / SessionInvalidationEventListener / AdminUserSessionController).
    }
}
