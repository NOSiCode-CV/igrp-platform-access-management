package cv.igrp.platform.access_management.users.application.listener;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
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

            // Only handle OIDC users — skip M2M/basic auth
            if (!(authentication.getPrincipal() instanceof IgrpOidcUser oidcUser)) {
                return;
            }

            UserProfile profile = oidcUser.getUserProfile();

            // Extract synchronously before any async call
            String externalId = profile.externalId();
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
}