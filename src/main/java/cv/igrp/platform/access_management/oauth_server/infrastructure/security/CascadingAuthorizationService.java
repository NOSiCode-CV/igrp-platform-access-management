package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Thin wrapper around {@link OAuth2AuthorizationService} that forwards every
 * operation to the JDBC delegate and adds two iGRP-specific cascades:
 *
 * <ul>
 *   <li><b>Phase C3 — revocation cascade.</b> After {@link #remove} succeeds,
 *       {@link RevocationCascadeListener#onAuthorizationRemoved(OAuth2Authorization)}
 *       revokes the linked {@code SessionEntity}. This is what makes
 *       {@code /oauth2/revoke} and {@code /connect/logout} flow through to the
 *       {@code SessionEnforcementFilter} so subsequent requests with the same
 *       JWT are rejected.</li>
 *   <li><b>FR-8 — refresh-token replay detection.</b> On {@link #save} we
 *       tombstone any previous refresh-token value via
 *       {@link RefreshTokenReuseGuard#recordRotation}; on {@link #findByToken}
 *       miss against a {@code REFRESH_TOKEN} we consult
 *       {@link RefreshTokenReuseGuard#detectReplay} so the linked session is
 *       revoked and a {@code SessionRevokedEvent} fires before Spring AS
 *       returns {@code invalid_grant}.</li>
 * </ul>
 */
public class CascadingAuthorizationService implements OAuth2AuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CascadingAuthorizationService.class);

    private final OAuth2AuthorizationService delegate;
    private final RevocationCascadeListener revocationCascadeListener;
    private final RefreshTokenReuseGuard refreshTokenReuseGuard;

    public CascadingAuthorizationService(OAuth2AuthorizationService delegate,
                                         RevocationCascadeListener revocationCascadeListener,
                                         RefreshTokenReuseGuard refreshTokenReuseGuard) {
        this.delegate = delegate;
        this.revocationCascadeListener = revocationCascadeListener;
        this.refreshTokenReuseGuard = refreshTokenReuseGuard;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        // Capture the previous state so we can tombstone a rotated refresh
        // token. The delegate persists in-place, so we must read BEFORE write.
        OAuth2Authorization previous = previousIfRefreshRotation(authorization);
        delegate.save(authorization);
        if (previous != null) {
            try {
                refreshTokenReuseGuard.recordRotation(previous, authorization);
            } catch (RuntimeException ex) {
                LOGGER.warn("Refresh-token tombstone failed for authorization {}: {}",
                        authorization.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        try {
            revocationCascadeListener.onAuthorizationRemoved(authorization);
        } catch (RuntimeException ex) {
            // Cascade failures must not poison the original revoke/logout flow:
            // the authorization is already gone, the enforcement filter will
            // simply find the SessionEntity stale on the next request.
            LOGGER.warn("Revocation cascade failed for authorization {}: {}",
                    authorization != null ? authorization.getId() : null, ex.getMessage());
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization result = delegate.findByToken(token, tokenType);
        if (result == null && tokenType != null
                && OAuth2TokenType.REFRESH_TOKEN.getValue().equals(tokenType.getValue())) {
            try {
                refreshTokenReuseGuard.detectReplay(token);
            } catch (RuntimeException ex) {
                LOGGER.warn("Refresh-token replay detection failed: {}", ex.getMessage());
            }
        }
        return result;
    }

    /**
     * Returns the persisted authorization if (a) it already exists in the
     * delegate, (b) it has a refresh token, and (c) the incoming authorization
     * also has a refresh token with a different value. Otherwise returns
     * {@code null} — saving a fresh authorization or an unrelated update is
     * not a rotation.
     */
    private OAuth2Authorization previousIfRefreshRotation(OAuth2Authorization incoming) {
        if (incoming == null || incoming.getId() == null) {
            return null;
        }
        var incomingRefresh = incoming.getRefreshToken();
        if (incomingRefresh == null || incomingRefresh.getToken() == null) {
            return null;
        }
        OAuth2Authorization existing;
        try {
            existing = delegate.findById(incoming.getId());
        } catch (RuntimeException ex) {
            LOGGER.debug("Could not load previous authorization {}: {}", incoming.getId(), ex.getMessage());
            return null;
        }
        if (existing == null || existing.getRefreshToken() == null
                || existing.getRefreshToken().getToken() == null) {
            return null;
        }
        String existingValue = existing.getRefreshToken().getToken().getTokenValue();
        String incomingValue = incomingRefresh.getToken().getTokenValue();
        if (existingValue == null || existingValue.equals(incomingValue)) {
            return null;
        }
        return existing;
    }
}
