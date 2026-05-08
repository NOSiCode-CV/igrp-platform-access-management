package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase E3 — OIDC RP-initiated logout cascade.
 *
 * <p>Wired as the {@code logoutResponseHandler} on
 * {@code OidcLogoutEndpointConfigurer}. When {@code /connect/logout} succeeds
 * we mark the bound {@link SessionEntity} as {@link SessionStatus#REVOKED}
 * with reason {@code USER_LOGOUT}, evict caches, publish
 * {@link SessionRevokedEvent}, and remove the underlying
 * {@link OAuth2Authorization} so subsequent JWT introspections /
 * resource-server requests are denied immediately by the enforcement chain.
 *
 * <p>The default redirect behavior of the OIDC logout endpoint is preserved:
 * when {@code post_logout_redirect_uri} was supplied we issue a 302 to it;
 * otherwise we return a bare 200.
 */
@Component
public class SessionLogoutHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionLogoutHandler.class);

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionHeartbeatService heartbeatService;
    private final OAuth2AuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public SessionLogoutHandler(SessionRepository sessionRepository,
                                SessionCacheEvictService sessionCacheEvictService,
                                SessionHeartbeatService heartbeatService,
                                OAuth2AuthorizationService authorizationService,
                                ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.heartbeatService = heartbeatService;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcLogoutAuthenticationToken token = (OidcLogoutAuthenticationToken) authentication;

        OidcIdToken idToken = token.getIdToken();
        UUID sid = extractSid(idToken);
        revokeBoundSession(sid);
        removeAuthorization(idToken);

        String postLogoutRedirectUri = token.getPostLogoutRedirectUri();
        if (StringUtils.hasText(postLogoutRedirectUri)) {
            String state = token.getState();
            String location = StringUtils.hasText(state)
                    ? appendQueryParam(postLogoutRedirectUri, "state", state)
                    : postLogoutRedirectUri;
            redirectStrategy.sendRedirect(request, response, location);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void revokeBoundSession(UUID sid) {
        if (sid == null) {
            return;
        }
        Optional<SessionEntity> entity = sessionRepository.findBySessionId(sid);
        if (entity.isEmpty()) {
            heartbeatService.evict(sid);
            return;
        }
        SessionEntity session = entity.get();
        if (SessionStatus.ACTIVE.equals(session.getStatus())) {
            session.revoke("USER_LOGOUT", "USER");
            sessionRepository.save(session);
        }
        heartbeatService.evict(sid);
        if (session.getUserId() != null) {
            sessionCacheEvictService.evictBySubject(session.getUserId());
        }
        eventPublisher.publishEvent(new SessionRevokedEvent(
                session.getSessionId(),
                session.getUserId(),
                "USER_LOGOUT",
                "USER"
        ));
        LOGGER.info("OIDC logout: revoked session {} (user={}) with reason USER_LOGOUT",
                sid, session.getUserId());
    }

    private void removeAuthorization(OidcIdToken idToken) {
        if (idToken == null) {
            return;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(
                idToken.getTokenValue(), null);
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
    }

    private static UUID extractSid(OidcIdToken idToken) {
        if (idToken == null) {
            return null;
        }
        Map<String, Object> claims = idToken.getClaims();
        if (claims == null) {
            return null;
        }
        Object raw = claims.get("sid");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String appendQueryParam(String url, String name, String value) {
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + name + "=" + value;
    }
}
