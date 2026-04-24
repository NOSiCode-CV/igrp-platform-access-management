package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.domain.models.AuthEventType;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Records Spring Security authentication events into the OAuth audit log.
 */
@Component
public class AuthAuditListener {

    private final AuthAuditService auditService;

    public AuthAuditListener(AuthAuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditService.log(usernameOf(event.getAuthentication()), AuthEventType.LOGIN_SUCCESS);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        auditService.log(usernameOf(event.getAuthentication()), AuthEventType.LOGIN_FAILURE);
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        auditService.log(usernameOf(event.getAuthentication()), AuthEventType.LOGOUT);
    }

    private static String usernameOf(Object authentication) {
        if (authentication == null) {
            return "anonymous";
        }
        try {
            return ((org.springframework.security.core.Authentication) authentication).getName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
