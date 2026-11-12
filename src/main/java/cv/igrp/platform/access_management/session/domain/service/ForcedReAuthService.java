package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Phase F1 — bumps {@code IGRPUserEntity.tokensNotValidBefore} to {@code now} so
 * the {@code SessionEnforcementFilter} rejects every JWT issued before this
 * instant, regardless of the bound session row.
 *
 * <p>Wired by callers performing a password reset or any "force the user to
 * re-authenticate everywhere" flow. The companion call to
 * {@link SessionInvalidationService#invalidateUserSession} kills the live
 * session rows; this floor closes the gap for any JWT that was already in
 * flight when those rows were revoked.
 */
@Service
public class ForcedReAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForcedReAuthService.class);

    private final IGRPUserEntityRepository userRepository;
    private final UserAccessKill kill;
    private final SessionAuditLogger sessionAuditLogger;

    public ForcedReAuthService(IGRPUserEntityRepository userRepository,
                               UserAccessKill kill,
                               SessionAuditLogger sessionAuditLogger) {
        this.userRepository = userRepository;
        this.kill = kill;
        this.sessionAuditLogger = sessionAuditLogger;
    }

    /**
     * Force the given user to re-authenticate: bump the token validity floor
     * AND revoke any live sessions in the same transaction. Callers should use
     * this for password resets and admin-driven "log this user out everywhere"
     * actions.
     *
     * @param actor NFR-4 actor token — {@code "SYSTEM"} for password-reset
     *              automation, {@link SessionAuditLogger#adminActor(String)}
     *              for an admin-initiated force-reauth.
     */
    @Transactional
    public void forceReAuthentication(String userId, String reason, String actor) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        int updated = userRepository.updateTokensNotValidBefore(userId, now);
        LOGGER.info("Forced re-auth for user={} reason={} actor={} floor={} rows={}",
                userId, reason, actor, now, updated);
        // A kill on the whole platform (#19): sessions, authorizations and keys.
        kill.kill(Set.of(userId), reason, actor);
        sessionAuditLogger.recordForcedReauth(userId, actor);
    }
}
