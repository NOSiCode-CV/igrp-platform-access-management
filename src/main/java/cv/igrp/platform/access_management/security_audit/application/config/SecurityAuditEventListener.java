package cv.igrp.platform.access_management.security_audit.application.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

/**
 * Bridges Spring Security events into the unified {@link SecurityAuditService}.
 *
 * <p>Since Phase 1 of the Unified Audit &amp; Reports feature this is the single
 * write path for authentication events — it absorbed the responsibilities of the
 * removed {@code AuthAuditEventListener} / {@code AuthAuditFailureListener}:
 * <ul>
 *   <li>bearer-JWT logins (resource-server) and OIDC browser logins
 *       (authorization-server) both record {@code LOGIN_SUCCESS};</li>
 *   <li>bad-credential failures record {@code LOGIN_FAILURE} while
 *       invalid/expired bearer tokens record {@code TOKEN_REJECTED}.</li>
 * </ul>
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
        Authentication auth = event.getAuthentication();

        // Resource-server path: a validated bearer JWT.
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String tokenId = jwt.getId() != null ? jwt.getId() : jwt.getClaimAsString("iat") + jwt.getSubject();
            if (auditedTokens.getIfPresent(tokenId) == null) {
                auditService.logEvent(
                        AuditEventType.LOGIN_SUCCESS,
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
            return;
        }

        // Authorization-server path: an OIDC browser login. Formerly handled by
        // AuthAuditEventListener; kept here so browser logins stay audited.
        if (auth instanceof OAuth2AuthenticationToken && auth.getPrincipal() instanceof OidcUser oidcUser) {
            String subject = oidcUser.getSubject() != null ? oidcUser.getSubject() : auth.getName();
            String dedupKey = "oauth2:" + subject;
            if (auditedTokens.getIfPresent(dedupKey) == null) {
                auditService.logEvent(
                        AuditEventType.LOGIN_SUCCESS,
                        AuditCategory.AUTHENTICATION,
                        Map.of("sub", String.valueOf(subject), "method", "OIDC_BROWSER")
                );
                auditedTokens.put(dedupKey, true);
            }
        }
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        AuthenticationException exception = event.getException();

        if (isTokenValidationFailure(exception)) {
            auditService.logEvent(
                    AuditEventType.TOKEN_REJECTED,
                    AuditCategory.AUTHENTICATION,
                    Map.of("reason", exception.getClass().getSimpleName())
            );
            return;
        }

        Object principal = event.getAuthentication() != null ? event.getAuthentication().getPrincipal() : null;
        auditService.logAuthenticationFailure(
                "Authentication failed"
                        + (principal != null ? " for principal: " + principal : "")
                        + " (" + exception.getClass().getSimpleName() + ")");
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        String permission = event.getAuthorizationResult().toString();
        auditService.logAccessDenied(permission);
    }

    private static boolean isTokenValidationFailure(AuthenticationException exception) {
        if (exception instanceof InvalidBearerTokenException) {
            return true;
        }
        return exception instanceof OAuth2AuthenticationException oauthEx
                && oauthEx.getError() != null
                && OAuth2ErrorCodes.INVALID_TOKEN.equals(oauthEx.getError().getErrorCode());
    }
}
