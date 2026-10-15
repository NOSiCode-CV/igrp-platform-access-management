package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    private final String tokenEndpoint;

    public CascadingAuthorizationService(OAuth2AuthorizationService delegate,
                                         RevocationCascadeListener revocationCascadeListener,
                                         RefreshTokenReuseGuard refreshTokenReuseGuard,
                                         String tokenEndpoint) {
        this.delegate = delegate;
        this.revocationCascadeListener = revocationCascadeListener;
        this.refreshTokenReuseGuard = refreshTokenReuseGuard;
        this.tokenEndpoint = tokenEndpoint;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        // Capture the previous state so we can tell what this save CHANGED.
        // The delegate persists in-place, so we must read BEFORE write.
        OAuth2Authorization previous = loadPrevious(authorization);
        delegate.save(authorization);
        if (previous == null) {
            return;
        }

        // A rotated refresh token: tombstone the old value so a later replay
        // of it is recognised (FR-8).
        if (isRefreshRotation(previous, authorization)) {
            try {
                refreshTokenReuseGuard.recordRotation(previous, authorization);
            } catch (RuntimeException ex) {
                LOGGER.warn("Refresh-token tombstone failed for authorization {}: {}",
                        authorization.getId(), ex.getMessage());
            }
        }

        // A revoked refresh token: cascade to the bound session.
        //
        // RFC 7009 revocation does NOT remove the authorization — Spring AS's
        // revocation provider marks the token invalidated and calls save().
        // The session cascade lived only in remove(), so /oauth2/revoke left
        // the SessionEntity ACTIVE: with JWT access tokens that meant
        // revocation had no effect on access whatsoever, since nothing
        // consults a revocation list on the resource-server path.
        //
        // Scoped to the REFRESH token deliberately. Revoking a refresh token
        // means "end this app's session", which is what the caller intends and
        // what the frontend expects. Revoking an access token alone means "stop
        // honouring this one token" — for a self-contained JWT we cannot do
        // that before its expiry, and tearing down the whole session in
        // response would be a far bigger action than was asked for.
        if (refreshTokenJustInvalidated(previous, authorization)) {
            try {
                revocationCascadeListener.revokeBoundSession(authorization, "TOKEN_REVOKED");
            } catch (RuntimeException ex) {
                LOGGER.warn("Revocation cascade failed for authorization {}: {}",
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
                && OAuth2TokenType.REFRESH_TOKEN.getValue().equals(tokenType.getValue())
                && servingTokenEndpoint()) {
            try {
                refreshTokenReuseGuard.detectReplay(token);
            } catch (RuntimeException ex) {
                LOGGER.warn("Refresh-token replay detection failed: {}", ex.getMessage());
            }
        }
        return result;
    }

    /**
     * Whether the current request is a grant at the token endpoint.
     *
     * <p>Replay detection is a protection on the GRANT: presenting a
     * rotated-out refresh token to {@code /oauth2/token} is the canonical theft
     * signature, and revoking the session is the right answer. Introspection and
     * revocation reach this same lookup, though, and a miss there is not an
     * attack — RFC 7009 explicitly says revoking an already-invalid token is a
     * no-op that returns 200. Without this guard, an application tidying up by
     * revoking the refresh token it had just rotated away destroyed its own
     * session, while revoking its CURRENT token did nothing at all (see save()).
     * That is precisely backwards.
     *
     * <p>Outside a request (scheduled work, startup) there is no grant in
     * flight, so there is nothing to protect and we stay silent.
     */
    private boolean servingTokenEndpoint() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return false;
        }
        String uri = attrs.getRequest().getRequestURI();
        // endsWith, not equals: behind the gateway the path may carry a
        // forwarded prefix such as /igrp-access-management/oauth2/token.
        return uri != null && uri.endsWith(tokenEndpoint);
    }

    /**
     * Returns the persisted authorization if (a) it already exists in the
     * delegate, (b) it has a refresh token, and (c) the incoming authorization
     * also has a refresh token with a different value. Otherwise returns
     * {@code null} — saving a fresh authorization or an unrelated update is
     * not a rotation.
     */
    /** The stored state of this authorization before the current save, if any. */
    private OAuth2Authorization loadPrevious(OAuth2Authorization incoming) {
        if (incoming == null || incoming.getId() == null) {
            return null;
        }
        if (incoming.getRefreshToken() == null) {
            // Nothing we inspect below applies without a refresh token.
            return null;
        }
        try {
            return delegate.findById(incoming.getId());
        } catch (RuntimeException ex) {
            LOGGER.debug("Could not load previous authorization {}: {}", incoming.getId(), ex.getMessage());
            return null;
        }
    }

    /** True when the refresh-token VALUE changed — i.e. it was rotated. */
    private static boolean isRefreshRotation(OAuth2Authorization previous, OAuth2Authorization incoming) {
        var previousRefresh = previous.getRefreshToken();
        var incomingRefresh = incoming.getRefreshToken();
        if (previousRefresh == null || previousRefresh.getToken() == null
                || incomingRefresh == null || incomingRefresh.getToken() == null) {
            return false;
        }
        String previousValue = previousRefresh.getToken().getTokenValue();
        String incomingValue = incomingRefresh.getToken().getTokenValue();
        return previousValue != null && !previousValue.equals(incomingValue);
    }

    /**
     * True when this save is what invalidated the refresh token — the signature
     * of an RFC 7009 revocation, as opposed to a rotation (value changed) or any
     * other in-place update.
     */
    private static boolean refreshTokenJustInvalidated(OAuth2Authorization previous,
                                                       OAuth2Authorization incoming) {
        var previousRefresh = previous.getRefreshToken();
        var incomingRefresh = incoming.getRefreshToken();
        if (previousRefresh == null || incomingRefresh == null) {
            return false;
        }
        return incomingRefresh.isInvalidated() && !previousRefresh.isInvalidated();
    }
}
