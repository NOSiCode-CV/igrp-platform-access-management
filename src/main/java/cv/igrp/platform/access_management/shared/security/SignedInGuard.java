package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Whether the caller of a self-service route is still signed in (#33; spec "Logout, kill and
 * refresh"). Routes with a key are covered by the key itself: a logout or kill deletes it, and
 * the cache miss that follows answers 401. Self-service routes have no key, so a user's token
 * is accepted only while the user holds an active authorization in AM's store, and stops
 * working at once after a logout or kill instead of when it expires.
 *
 * <p>A service account's token has no sign-in behind it and is accepted as before. A user who
 * is no longer signed in gets 401, as for an expired token, not 403.
 */
@Component
public class SignedInGuard {

    private final UserAuthorizations authorizations;

    public SignedInGuard(UserAuthorizations authorizations) {
        this.authorizations = authorizations;
    }

    /**
     * Allows a signed-in caller; denies one with no token.
     *
     * @throws CredentialsExpiredException for a user who is no longer signed in, answered with 401
     */
    public AuthorizationDecision check(Authentication auth) {
        if (jwt(auth) == null) {
            return new AuthorizationDecision(false);
        }
        if (!isSignedIn(auth)) {
            throw new CredentialsExpiredException("The sign-in has ended: signed out or access ended");
        }
        return new AuthorizationDecision(true);
    }

    public boolean isSignedIn(Authentication auth) {
        Jwt jwt = jwt(auth);
        if (jwt == null) {
            return false;
        }
        if (ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT.equals(
                jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE))) {
            return true;
        }
        try {
            return authorizations.hasActive(SubjectParser.parseUserSubjectOrThrow(jwt.getSubject()));
        } catch (InvalidPrincipalException ex) {
            return false;
        }
    }

    private static Jwt jwt(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth instanceof OidcContextAuthenticationToken oidcToken) {
            return oidcToken.getJwt();
        }
        return auth.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }
}
