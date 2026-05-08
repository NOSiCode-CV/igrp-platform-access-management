package cv.igrp.platform.access_management.session.application.listener;

import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener for session-related events
 */
@Slf4j
@Component
public class SessionEventListener {

    private final SessionInvalidationService sessionInvalidationService;

    public SessionEventListener(SessionInvalidationService sessionInvalidationService) {
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @EventListener
    public void handleSessionCreated(cv.igrp.platform.access_management.session.domain.event.SessionCreatedEvent event) {
        log.info("Session created: {} for user: {} from IP: {}", 
                event.getSessionId(), event.getUserId(), event.getClientIp());
        
        // Could trigger audit logging, notifications, etc.
    }

    @EventListener
    public void handleSessionClosed(cv.igrp.platform.access_management.session.domain.event.SessionClosedEvent event) {
        log.info("Session closed: {} for user: {} with reason: {} by: {}", 
                event.getSessionId(), event.getUserId(), event.getReason(), event.getClosedBy());
        
        // Could trigger audit logging, notifications, etc.
    }

    @EventListener
    public void handleSessionExpired(cv.igrp.platform.access_management.session.domain.event.SessionExpiredEvent event) {
        log.info("Session expired: {} for user: {}", 
                event.getSessionId(), event.getUserId());
        
        // Could trigger audit logging, notifications, etc.
    }

    @EventListener
    public void handleSessionRevoked(cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent event) {
        log.warn("Session revoked: {} for user: {} with reason: {} by: {}", 
                event.getSessionId(), event.getUserId(), event.getReason(), event.getRevokedBy());
        
        // Could trigger audit logging, security alerts, etc.
    }
}
