package cv.igrp.platform.access_management.users.application.listener;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Listens for every authenticated request and enriches the user's profile
 * with any missing JWT claims (phone, nic, name, email).
 *
 * This listener NEVER creates users — that is exclusively the responsibility
 * of RespondUserInvitationCommandHandler (invite-accept flow).
 *
 * Coexists with AuthAuditEventListener — both listen to the same event
 * as independent Spring beans.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserIdentityResolutionListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final UserIdentityResolutionService identityResolutionService;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        try {
            Authentication authentication = event.getAuthentication();

            // M2M / service-account tokens have no user identity to enrich.
            // The IgrpJwtAuthenticationConverter wraps every JWT into an
            // IgrpOidcUser, so we can't rely on the principal type alone —
            // inspect the underlying JWT for the principal-type marker emitted
            // by ClaimsEnrichmentService for client_credentials issuances.
            if (isServiceAccountToken(authentication)) {
                return;
            }

            // Only handle OIDC users — skip basic auth and any other non-OIDC
            // authentication tokens.
            if (!(authentication.getPrincipal() instanceof IgrpOidcUser oidcUser)) {
                return;
            }

            UserProfile profile = oidcUser.getUserProfile();

            // Extract synchronously before any async call
            String externalId = profile.id();
            String email = nullIfBlank(profile.email());
            String phone = nullIfBlank(profile.phone());
            String nic = nullIfBlank(profile.nic());
            String fullName = profile.fullName();

            // Delegate enrichment asynchronously — never blocks the request
            identityResolutionService.resolveOrEnrichAsync(externalId, email, nic, phone, fullName);

        } catch (Exception e) {
            // Never rethrow — resolution failure must never deny access
            log.warn("[IDENTITY] User resolution listener error: {}", e.getMessage());
        }
    }

    private static String nullIfBlank(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }

    /**
     * True when the authenticated JWT was issued via the
     * {@code client_credentials} grant for a service account. We rely on the
     * {@code principalType} claim emitted by
     * {@code ClaimsEnrichmentService} rather than on the principal class
     * because every JWT — user or M2M — gets wrapped into an
     * {@code IgrpOidcUser} by the JWT converter.
     */
    private static boolean isServiceAccountToken(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            return false;
        }
        String principalType = jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE);
        if (ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT.equals(principalType)) {
            return true;
        }
        // Fallback: a JWT carrying client_id with no sid is the canonical M2M
        // shape even when the principalType marker isn't present yet (e.g.
        // tokens issued before V9 / for clients without a service account
        // record yet).
        String sid = jwt.getClaimAsString("sid");
        String clientId = jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_CLIENT_ID);
        String sub = jwt.getSubject();
        return (sid == null || sid.isBlank())
                && clientId != null
                && sub != null
                && sub.equals(clientId);
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof OidcContextAuthenticationToken oidcToken) {
            return oidcToken.getJwt();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
