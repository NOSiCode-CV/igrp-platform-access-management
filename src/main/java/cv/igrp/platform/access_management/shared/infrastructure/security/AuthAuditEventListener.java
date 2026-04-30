package cv.igrp.platform.access_management.shared.infrastructure.security;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final AuthAuditService authAuditService;

    public AuthAuditEventListener(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        HttpServletRequest request = null;
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
            request = servletAttributes.getRequest();
        }

        if (event.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, request);
            authAuditService.logSuccess(context);
            return;
        }

        if (event.getAuthentication() instanceof OAuth2AuthenticationToken
                && event.getAuthentication().getPrincipal() instanceof IgrpOidcUser oidcUser) {
            AuthAuditContext context = AuthAuditService.fromUserProfile(oidcUser.getUserProfile(), request);
            authAuditService.logSuccess(context);
        }
    }

}
