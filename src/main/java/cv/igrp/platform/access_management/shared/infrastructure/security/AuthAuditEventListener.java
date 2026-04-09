package cv.igrp.platform.access_management.shared.infrastructure.security;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditEventListener.class);
    private final AuthAuditService authAuditService;

    public AuthAuditEventListener(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
        log.info("[AUDIT] AuthAuditEventListener initialized");
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            
            HttpServletRequest request = null;
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
                request = servletAttributes.getRequest();
            }

            AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, request);
            authAuditService.logSuccess(context);
        }
    }
}
