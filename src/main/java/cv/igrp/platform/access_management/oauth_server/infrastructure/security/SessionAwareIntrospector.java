package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.http.converter.OAuth2TokenIntrospectionHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase E2 — session-aware {@code /oauth2/introspect} response handler.
 *
 * <p>The default Spring Authorization Server introspector reports {@code active=true}
 * whenever the persisted {@link OAuth2Token} is structurally valid and unexpired.
 * For iGRP-issued tokens we additionally consult the bound {@link SessionEntity}
 * (via the {@code sid} claim) and force {@code active=false} when the session is
 * not {@link SessionStatus#ACTIVE} or has passed its absolute lifetime — closing
 * the gap between server-side session revocation and JWT-only signature checks.
 *
 * <p>Non-iGRP tokens (M2M / client_credentials, opaque tokens without {@code sid})
 * are passed through unchanged so the override never weakens the standard
 * introspection contract for clients that never rely on session binding.
 */
@Component
public class SessionAwareIntrospector implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAwareIntrospector.class);

    private final SessionRepository sessionRepository;
    private final HttpMessageConverter<OAuth2TokenIntrospection> introspectionConverter =
            new OAuth2TokenIntrospectionHttpMessageConverter();

    public SessionAwareIntrospector(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2TokenIntrospection introspection = ((OAuth2TokenIntrospectionAuthenticationToken) authentication)
                .getTokenClaims();

        OAuth2TokenIntrospection effective = applySessionGuard(introspection);

        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.OK);
        introspectionConverter.write(effective, null, httpResponse);
    }

    private OAuth2TokenIntrospection applySessionGuard(OAuth2TokenIntrospection introspection) {
        if (!Boolean.TRUE.equals(introspection.isActive())) {
            return introspection;
        }
        Map<String, Object> claims = introspection.getClaims();
        Object rawSid = claims != null ? claims.get("sid") : null;
        if (rawSid == null) {
            // Non-iGRP token (no session binding) — pass through.
            return introspection;
        }
        UUID sid;
        try {
            sid = UUID.fromString(rawSid.toString());
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Introspect: malformed sid claim {} — returning active=false defensively",
                    rawSid);
            return inactive();
        }

        Optional<SessionEntity> entity = sessionRepository.findBySessionId(sid);
        if (entity.isEmpty()) {
            return inactive();
        }
        SessionEntity session = entity.get();
        Instant now = Instant.now();
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            return inactive();
        }
        if (session.getAbsoluteExpiresAt() != null && !now.isBefore(session.getAbsoluteExpiresAt())) {
            return inactive();
        }
        if (session.getExpiresAt() != null && !now.isBefore(session.getExpiresAt())) {
            return inactive();
        }
        return introspection;
    }

    private static OAuth2TokenIntrospection inactive() {
        return OAuth2TokenIntrospection.builder().build();
    }
}
