package cv.igrp.platform.access_management.shared.infrastructure.security;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAuditFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditFailureListener.class);
    private final AuthAuditService authAuditService;

    public AuthAuditFailureListener(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationFailureEvent event) {
        HttpServletRequest request = null;
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
            request = servletAttributes.getRequest();
        }

        AuthAuditContext context = new AuthAuditContext(
            IdentifierType.UNKNOWN,
            null,
            null,
            null,
            null,
            request
        );

        String reason = event.getException().getClass().getSimpleName();
        authAuditService.logFailure(context, reason);
    }
}
