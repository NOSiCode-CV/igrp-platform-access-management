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
 */
@Component("subjectGuard")
public class SubjectGuard {

    /**
     * @return {@code true} when the authentication principal is a JWT that
     *         carries a non-blank {@code sid} claim (i.e. an interactive user
     *         session). {@code false} for null authentication, non-JWT
     *         principals, or sid-less JWTs (typical M2M shape).
     */
    public boolean requiresUser(Authentication auth) {
        if (auth == null) {
            return false;
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return false;
        }
        String sid = jwt.getClaimAsString("sid");
        return sid != null && !sid.isBlank();
    }
}
