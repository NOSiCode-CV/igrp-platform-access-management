package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Phase C3 — thin wrapper around {@link OAuth2AuthorizationService} that
 * forwards every operation to the JDBC delegate and additionally invokes
 * {@link RevocationCascadeListener#onAuthorizationRemoved(OAuth2Authorization)}
 * after a successful {@link #remove(OAuth2Authorization)} call. This is what
 * makes {@code /oauth2/revoke} and {@code /connect/logout} flow through to a
 * server-side {@code SessionEntity.revoke()} so the enforcement filter denies
 * subsequent requests carrying the now-defunct JWT.
 */
public class CascadingAuthorizationService implements OAuth2AuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CascadingAuthorizationService.class);

    private final OAuth2AuthorizationService delegate;
    private final RevocationCascadeListener revocationCascadeListener;

    public CascadingAuthorizationService(OAuth2AuthorizationService delegate,
                                         RevocationCascadeListener revocationCascadeListener) {
        this.delegate = delegate;
        this.revocationCascadeListener = revocationCascadeListener;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
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
        return delegate.findByToken(token, tokenType);
    }
}
