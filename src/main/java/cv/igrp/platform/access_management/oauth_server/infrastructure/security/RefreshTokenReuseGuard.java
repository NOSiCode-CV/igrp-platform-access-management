package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import cv.igrp.platform.access_management.security_audit.application.service.AccessAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Detects a replay of a refresh token that Spring Authorization Server has already rotated
 * out, and then ends the user's access on the whole platform (#33): every authorization is
 * removed and their permission keys are deleted after commit, as a kill does.
 *
 * <p>The guard collaborates with {@link CascadingAuthorizationService}:
 * <ul>
 *   <li>{@link #recordRotation(OAuth2Authorization, OAuth2Authorization)} is called on every
 *       successful rotation ({@code reuseRefreshTokens(false)}) to tombstone the old value;</li>
 *   <li>{@link #detectReplay(String)} is called whenever the wrapper's
 *       {@code findByToken(token, REFRESH_TOKEN)} returns {@code null}. A hit means the caller
 *       presented a previously rotated value, which is the canonical replay signature.</li>
 * </ul>
 *
 * <p>Both methods are best-effort: failures inside the guard never throw out of the
 * authorization-server pipeline, so the upstream {@code invalid_grant} still goes out.
 */
@Component
public class RefreshTokenReuseGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenReuseGuard.class);

    static final String REVOKE_REASON = "REFRESH_TOKEN_REUSE";

    private final RefreshTokenTombstoneRepository tombstoneRepository;
    private final UserAuthorizations userAuthorizations;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessAuditLogger accessAuditLogger;

    public RefreshTokenReuseGuard(RefreshTokenTombstoneRepository tombstoneRepository,
                                  UserAuthorizations userAuthorizations,
                                  ApplicationEventPublisher eventPublisher,
                                  AccessAuditLogger accessAuditLogger) {
        this.tombstoneRepository = tombstoneRepository;
        this.userAuthorizations = userAuthorizations;
        this.eventPublisher = eventPublisher;
        this.accessAuditLogger = accessAuditLogger;
    }

    /**
     * Tombstone the previous refresh-token value when rotation has just occurred.
     * No-op when the previous authorization has no refresh token, or when the value
     * is unchanged (no rotation actually happened).
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

        String userId = extractUserId(next, previous);
        Instant expiresAt = Optional.ofNullable(previousRefresh.getToken().getExpiresAt())
                .orElseGet(() -> Instant.now().plusSeconds(86_400));

        String hash = sha256(previousValue);
        try {
            RefreshTokenTombstoneEntity entity = tombstoneRepository
                    .findByTokenHash(hash)
                    .orElseGet(RefreshTokenTombstoneEntity::new);
            entity.setTokenHash(hash);
            entity.setUserId(userId);
            entity.setInvalidatedAt(Instant.now());
            entity.setExpiresAt(expiresAt);
            tombstoneRepository.save(entity);
            LOGGER.debug("Tombstoned rotated refresh token (user={} exp={})", userId, expiresAt);
        } catch (RuntimeException ex) {
            // Rotation must not fail because we couldn't write a tombstone.
            LOGGER.warn("Could not tombstone rotated refresh token (user={}): {}", userId, ex.getMessage());
        }
    }

    /**
     * Detect a replay of a previously rotated refresh token. On a hit, end the user's access on
     * the whole platform.
     *
     * @return {@code true} when a tombstone matched (replay detected), {@code false} otherwise.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean detectReplay(String presentedTokenValue) {
        if (presentedTokenValue == null || presentedTokenValue.isBlank()) {
            return false;
        }
        Optional<RefreshTokenTombstoneEntity> hit = tombstoneRepository.findByTokenHash(sha256(presentedTokenValue));
        if (hit.isEmpty()) {
            return false;
        }
        String userId = hit.get().getUserId();
        LOGGER.warn("Detected refresh-token replay (user={}); ending the user's access", userId);
        if (userId == null) {
            return true;
        }
        try {
            int removed = userAuthorizations.removeAll(userId);
            accessAuditLogger.recordAccessEnded(userId, REVOKE_REASON, AccessAuditLogger.SYSTEM);
            eventPublisher.publishEvent(new UserAccessEndedEvent(Set.of(userId), REVOKE_REASON));
            LOGGER.info("Refresh-token replay: removed {} authorization(s) of user={}", removed, userId);
        } catch (RuntimeException ex) {
            // Never poison the upstream invalid_grant response.
            LOGGER.warn("Replay-detection side effects failed for user={}: {}", userId, ex.getMessage());
        }
        return true;
    }

    private static String extractUserId(OAuth2Authorization next, OAuth2Authorization previous) {
        String userId = userIdFrom(next);
        return userId != null ? userId : userIdFrom(previous);
    }

    private static String userIdFrom(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        String principalName = authorization.getPrincipalName();
        if (principalName == null || principalName.isBlank()) {
            return null;
        }
        try {
            UUID.fromString(principalName);
            return principalName;
        } catch (IllegalArgumentException ex) {
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
