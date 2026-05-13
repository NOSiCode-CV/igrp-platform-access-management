package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Phase G1 / FR-13 — Spring bean exposed to SpEL as {@code @subjectGuard} for
 * use in {@code @PreAuthorize} expressions on controllers that require a real
 * user principal (i.e. an authorization-code session token carrying a
 * {@code sid} claim). Belt-and-suspenders defense alongside the
 * {@link M2MTokenRejectionFilter} on the OAuth2 chain and the
 * {@link SubjectParser} safety net at every parse site.
 *
 * <p>Usage:
 * <pre>{@code
 * @PreAuthorize("@subjectGuard.requiresUser(authentication)")
 * }</pre>
 *
 * <p>Two authentication shapes are accepted (mirroring
 * {@link AuthenticationHelper#getSub()}):
 * <ul>
 *   <li>{@link OidcContextAuthenticationToken} (the production shape produced
 *       by {@link IgrpJwtAuthenticationConverter} — principal is
 *       {@link IgrpOidcUser}, credentials hold the raw {@link Jwt}).</li>
 *   <li>A raw {@link Jwt} principal (the resource-server default shape used
 *       by test fixtures and any path that hasn't been converted).</li>
 * </ul>
 * Anything else (null auth, M2M {@code UsernamePasswordAuthenticationToken},
 * anonymous, etc.) is denied.
 */
@Component("subjectGuard")
public class SubjectGuard {

    /**
     * @return {@code true} when the authentication carries a JWT with a
     *         non-blank {@code sid} claim (i.e. an interactive user session).
     *         {@code false} for null authentication, non-JWT authentications,
     *         or sid-less JWTs (typical M2M shape).
     */
    public boolean requiresUser(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        if (jwt == null) {
            return false;
        }
        String sid = jwt.getClaimAsString("sid");
        return sid != null && !sid.isBlank();
    }

    /**
     * Extract the underlying {@link Jwt} from either shape of authentication.
     * Returns {@code null} when no JWT is present (null auth, M2M token, etc.).
     */
    private static Jwt extractJwt(Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth instanceof OidcContextAuthenticationToken oidcToken) {
            return oidcToken.getJwt();
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
