package cv.igrp.platform.access_management.security_audit.application.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

/**
 * Component that listens for Spring Security events and logs them using the SecurityAuditService.
 * This provides a seamless way to capture authentication and authorization events.
 */
@Component
public class SecurityAuditEventListener {

    private final SecurityAuditService auditService;

    private final Cache<String, Boolean> auditedTokens = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS) // Match your JWT expiry
            .maximumSize(10000)
            .build();

    public SecurityAuditEventListener(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        var auth = event.getAuthentication();

        if (auth.getPrincipal() instanceof Jwt jwt) {
            String tokenId = jwt.getId() != null ? jwt.getId() : jwt.getClaimAsString("iat") + jwt.getSubject();

            // If we haven't seen this token in this session window, log it as a "Login"
            if (auditedTokens.getIfPresent(tokenId) == null) {
                auditService.logEvent(
                        AuditEventType.LOGIN_SUCCESS, // Maps to the "Login" intent of ASVS
                        AuditCategory.AUTHENTICATION,
                        Map.of(
                                "tokenId", tokenId,
                                "sub", jwt.getSubject(),
                                "aud", String.join(",", ofNullable(jwt.getAudience()).orElse(new ArrayList<>())),
                                "iss", jwt.getIssuer().toString(),
                                "method", "OIDC"
                        )
                );
                auditedTokens.put(tokenId, true);
            }
        }
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = (String) event.getAuthentication().getPrincipal();
        auditService.logAuthenticationFailure("Bad credentials for user: " + username);
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        String permission = event.getAuthorizationResult().toString(); // Customize as needed
        auditService.logAccessDenied(permission);
    }
}