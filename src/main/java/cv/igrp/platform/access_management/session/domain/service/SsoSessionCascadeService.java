package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Revokes the sibling sessions opened under the same AM sign-in (R2.1–R2.4).
 *
 * <p>Without this, signing out of one application left every other application
 * on the same browser signed in — each had its own {@code t_user_session} row,
 * and the refresh grant is a back-channel call authenticated by client
 * credentials that never consults the browser session, so invalidating the
 * cookie did not reach it. In practice those applications ran to the absolute
 * ceiling, eight hours by default, because each successful refresh slid the idle
 * deadline forward.
 *
 * <p><b>A separate bean on purpose.</b> R2.4 requires the cascade to run in its
 * own transaction so a failure here cannot roll back the bound row's revocation,
 * the servlet-session invalidation or the upstream IdP cascade.
 * {@code @Transactional(REQUIRES_NEW)} is applied by a proxy, and a proxy is
 * bypassed by self-invocation — so had this stayed a private method on
 * {@code SessionLogoutHandler} the annotation would have been silently inert and
 * the isolation imaginary.
 *
 * <p>Rows are revoked; sibling {@code OAuth2Authorization} records are
 * deliberately left alone (R2.3/D3). A revoked row is already enough to stop
 * every path: {@code applyRefresh} rejects a non-ACTIVE session with
 * {@code invalid_grant}, {@code SessionEnforcementFilter} answers
 * {@code 401 session_revoked}, and {@code /api/session/check} reports
 * {@code valid=false} with the reason. Removing authorizations as well would
 * double the write surface to achieve what one column already does.
 */
@Service
public class SsoSessionCascadeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SsoSessionCascadeService.class);

    private static final String REASON_USER_LOGOUT = "USER_LOGOUT";

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionHeartbeatService heartbeatService;
    private final SessionAuditLogger sessionAuditLogger;
    private final ApplicationEventPublisher eventPublisher;

    public SsoSessionCascadeService(SessionRepository sessionRepository,
                                    SessionCacheEvictService sessionCacheEvictService,
                                    SessionHeartbeatService heartbeatService,
                                    SessionAuditLogger sessionAuditLogger,
                                    ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.heartbeatService = heartbeatService;
        this.sessionAuditLogger = sessionAuditLogger;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Revoke every other ACTIVE session sharing {@code ssoSessionKey}.
     *
     * <p>Call after the bound session has already been revoked. The bound row is
     * skipped by {@code excludeSid} rather than revoked twice, so its audit entry
     * and {@code SessionRevokedEvent} are not duplicated.
     *
     * @param userId      owner of the sessions; scopes the query alongside the key.
     * @param ssoSessionKey the sign-in key from the bound row; {@code null} or
     *                    blank means the bound row had none, so there is nothing
     *                    to cascade to and the bound row stands alone (R2.1).
     * @param excludeSid  the already-revoked bound session.
     * @return how many sibling rows were revoked.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeSiblings(String userId, String ssoSessionKey, UUID excludeSid) {
        if (userId == null || ssoSessionKey == null || ssoSessionKey.isBlank()) {
            return 0;
        }

        List<SessionEntity> siblings = sessionRepository
                .findByUserIdAndSsoSessionKeyAndStatus(userId, ssoSessionKey, SessionStatus.ACTIVE);

        int revoked = 0;
        for (SessionEntity sibling : siblings) {
            if (excludeSid != null && excludeSid.equals(sibling.getSessionId())) {
                continue;
            }
            // R2.2 — identical treatment to the bound row, so an application
            // cannot tell from its own vantage point whether it was the one that
            // logged out. Reason USER_LOGOUT specifically: it must be
            // distinguishable from SESSION_LIMIT_EXCEEDED, because a cap
            // eviction is not a sign-out and applications are expected to treat
            // the two differently (V7).
            sibling.revoke(REASON_USER_LOGOUT, SessionAuditLogger.USER);
            sessionRepository.save(sibling);
            heartbeatService.evict(sibling.getSessionId());
            sessionAuditLogger.recordRevoked(sibling.getSessionId(), sibling.getUserId(),
                    REASON_USER_LOGOUT, SessionAuditLogger.USER);
            eventPublisher.publishEvent(new SessionRevokedEvent(
                    sibling.getSessionId(),
                    sibling.getUserId(),
                    REASON_USER_LOGOUT,
                    SessionAuditLogger.USER
            ));
            revoked++;
        }

        if (revoked > 0) {
            // Once, after the loop: the cache is keyed by subject, so evicting
            // per row would repeat identical work for every application.
            sessionCacheEvictService.evictBySubject(userId);
            LOGGER.info("Logout cascade revoked {} sibling session(s) for user={} ssoKey={}",
                    revoked, userId, SsoSessionKey.fingerprint(ssoSessionKey));
        } else {
            LOGGER.debug("Logout cascade found no sibling sessions for user={} ssoKey={}",
                    userId, SsoSessionKey.fingerprint(ssoSessionKey));
        }
        return revoked;
    }
}
