package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-8 (see {@code _specs/session/requirements.md}) — detect replay of a
 * refresh token that has already been rotated out by Spring Authorization
 * Server, revoke the linked {@link SessionEntity}, and publish a
 * {@link SessionRevokedEvent} with reason {@code REFRESH_TOKEN_REUSE}.
 *
 * <p>The guard collaborates with {@link CascadingAuthorizationService}:
 * <ul>
 *   <li>{@link #recordRotation(OAuth2Authorization, OAuth2Authorization)}
 *       is called on every successful refresh-token rotation
 *       ({@code reuseRefreshTokens(false)}) to tombstone the OLD token value.</li>
 *   <li>{@link #detectReplay(String)} is called whenever the wrapper's
 *       {@code findByToken(token, REFRESH_TOKEN)} returns {@code null}. A hit
 *       means the caller presented a previously-rotated value, which is the
 *       canonical replay signature.</li>
 * </ul>
 *
 * <p>Both methods are best-effort: failures inside the guard never throw out
 * of the authorization-server pipeline — they're logged so the upstream
 * {@code invalid_grant} response still reaches the attacker on schedule.
 */
@Component
public class RefreshTokenReuseGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenReuseGuard.class);

    static final String REVOKE_REASON = "REFRESH_TOKEN_REUSE";

    private final RefreshTokenTombstoneRepository tombstoneRepository;
    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final ApplicationEventPublisher eventPublisher;

    public RefreshTokenReuseGuard(RefreshTokenTombstoneRepository tombstoneRepository,
                                  SessionRepository sessionRepository,
                                  SessionCacheEvictService sessionCacheEvictService,
                                  ApplicationEventPublisher eventPublisher) {
        this.tombstoneRepository = tombstoneRepository;
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Tombstone the previous refresh-token value when rotation has just occurred.
     * No-op when the previous authorization has no refresh token, when the value
     * is unchanged (no rotation actually happened), or when the sid claim cannot
     * be resolved (non-iGRP authorization).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRotation(OAuth2Authorization previous, OAuth2Authorization next) {
        if (previous == null || next == null) {
            return;
        }
        OAuth2Authorization.Token<org.springframework.security.oauth2.core.OAuth2RefreshToken> previousRefresh
                = previous.getRefreshToken();
        OAuth2Authorization.Token<org.springframework.security.oauth2.core.OAuth2RefreshToken> nextRefresh
                = next.getRefreshToken();
        if (previousRefresh == null || previousRefresh.getToken() == null) {
            return;
        }
        if (nextRefresh == null || nextRefresh.getToken() == null) {
            return;
        }
        String previousValue = previousRefresh.getToken().getTokenValue();
        String nextValue = nextRefresh.getToken().getTokenValue();
        if (previousValue == null || previousValue.equals(nextValue)) {
            return; // not a rotation
        }

        UUID sid = extractSid(next, previous);
        Integer userId = extractUserId(next, previous);
        Instant expiresAt = Optional.ofNullable(previousRefresh.getToken().getExpiresAt())
                .orElseGet(() -> Instant.now().plusSeconds(86_400));

        String hash = sha256(previousValue);
        try {
            RefreshTokenTombstoneEntity entity = tombstoneRepository
                    .findByTokenHash(hash)
                    .orElseGet(RefreshTokenTombstoneEntity::new);
            entity.setTokenHash(hash);
            entity.setSessionId(sid);
            entity.setUserId(userId);
            entity.setInvalidatedAt(Instant.now());
            entity.setExpiresAt(expiresAt);
            tombstoneRepository.save(entity);
            LOGGER.debug("Tombstoned rotated refresh token (sid={} user={} exp={})",
                    sid, userId, expiresAt);
        } catch (RuntimeException ex) {
            // Rotation must not fail because we couldn't write a tombstone.
            LOGGER.warn("Could not tombstone rotated refresh token (sid={}): {}",
                    sid, ex.getMessage());
        }
    }

    /**
     * Detect a replay of a previously rotated refresh token. On a hit, revoke
     * the linked session (idempotently) and publish {@link SessionRevokedEvent}.
     *
     * @return {@code true} when a tombstone matched (replay detected),
     *         {@code false} otherwise.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean detectReplay(String presentedTokenValue) {
        if (presentedTokenValue == null || presentedTokenValue.isBlank()) {
            return false;
        }
        String hash = sha256(presentedTokenValue);
        Optional<RefreshTokenTombstoneEntity> hit = tombstoneRepository.findByTokenHash(hash);
        if (hit.isEmpty()) {
            return false;
        }
        RefreshTokenTombstoneEntity tombstone = hit.get();
        UUID sid = tombstone.getSessionId();
        Integer userId = tombstone.getUserId();
        LOGGER.warn("Detected refresh-token replay (sid={} user={}); revoking session", sid, userId);

        try {
            if (sid != null) {
                Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionId(sid);
                sessionOpt.ifPresent(session -> {
                    if (SessionStatus.ACTIVE.equals(session.getStatus())) {
                        session.revoke(REVOKE_REASON, "SYSTEM");
                        sessionRepository.save(session);
                    }
                });
                if (userId != null) {
                    sessionCacheEvictService.evictBySubject(userId);
                }
                eventPublisher.publishEvent(new SessionRevokedEvent(sid, userId, REVOKE_REASON, "SYSTEM"));
            } else {
                LOGGER.debug("Replay tombstone hit without a sid — nothing to revoke");
            }
        } catch (RuntimeException ex) {
            // Never poison the upstream invalid_grant response.
            LOGGER.warn("Replay-detection side effects failed for sid={}: {}", sid, ex.getMessage());
        }
        return true;
    }

    private static UUID extractSid(OAuth2Authorization next, OAuth2Authorization previous) {
        UUID sid = sidFromAccessToken(next);
        return sid != null ? sid : sidFromAccessToken(previous);
    }

    private static Integer extractUserId(OAuth2Authorization next, OAuth2Authorization previous) {
        Integer userId = userIdFrom(next);
        return userId != null ? userId : userIdFrom(previous);
    }

    private static UUID sidFromAccessToken(OAuth2Authorization authorization) {
        if (authorization == null || authorization.getAccessToken() == null) {
            return null;
        }
        Map<String, Object> claims = authorization.getAccessToken().getClaims();
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

    private static Integer userIdFrom(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        String principalName = authorization.getPrincipalName();
        if (principalName == null) {
            return null;
        }
        try {
            return Integer.parseInt(principalName);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
