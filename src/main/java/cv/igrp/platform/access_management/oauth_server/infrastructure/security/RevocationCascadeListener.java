package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase C3 — when the authorization-server pipeline removes an
 * {@link OAuth2Authorization} (token revocation, OIDC RP-initiated logout,
 * authorization-code one-shot consume, etc.) the corresponding
 * {@link SessionEntity} is revoked too. Without this cascade, the JWT itself
 * keeps validating until {@code exp}; with it, the {@link SessionEnforcementFilter}
 * sees the next request as {@code session_revoked} immediately.
 *
 * <p>Wired by {@link AuthorizationServerConfig} into the
 * {@link CascadingAuthorizationService} wrapper around the JDBC delegate.
 */
@Component
public class RevocationCascadeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevocationCascadeListener.class);

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionHeartbeatService heartbeatService;
    private final SessionAuditLogger sessionAuditLogger;

    public RevocationCascadeListener(SessionRepository sessionRepository,
                                     SessionCacheEvictService sessionCacheEvictService,
                                     SessionHeartbeatService heartbeatService,
                                     SessionAuditLogger sessionAuditLogger) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.heartbeatService = heartbeatService;
        this.sessionAuditLogger = sessionAuditLogger;
    }

    /**
     * Called from {@link CascadingAuthorizationService#remove(OAuth2Authorization)}
     * after the JDBC delegate has dropped the authorization row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuthorizationRemoved(OAuth2Authorization authorization) {
        if (authorization == null) {
            return;
        }
        UUID sid = extractSid(authorization);
        if (sid == null) {
            // Non-iGRP / M2M authorizations are not bound to a SessionEntity.
            return;
        }
        Optional<SessionEntity> entity = sessionRepository.findBySessionId(sid);
        if (entity.isEmpty()) {
            heartbeatService.evict(sid);
            return;
        }
        SessionEntity session = entity.get();
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            heartbeatService.evict(sid);
            return;
        }
        session.revoke("OAUTH_AUTHORIZATION_REMOVED", "SYSTEM");
        sessionRepository.save(session);
        heartbeatService.evict(sid);
        if (session.getUserId() != null) {
            sessionCacheEvictService.evictBySubject(session.getUserId());
        }
        sessionAuditLogger.recordRevoked(session.getSessionId(), session.getUserId(),
                "OAUTH_AUTHORIZATION_REMOVED", SessionAuditLogger.SYSTEM);
        LOGGER.info("Revoked session {} (user={}) following OAuth2 authorization removal {}",
                sid, session.getUserId(), authorization.getId());
    }

    private static UUID extractSid(OAuth2Authorization authorization) {
        return sidFromToken(
                authorization.getAccessToken() != null
                        ? authorization.getAccessToken().getClaims()
                        : null);
    }

    private static UUID sidFromToken(Map<String, Object> claims) {
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
}
