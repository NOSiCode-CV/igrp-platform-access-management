package cv.igrp.platform.access_management.shared.security.policy;

import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Policy that checks if the current subject is the owner of the resource.
 */
@Component
public class OwnershipPolicy implements Policy {

    private final AuthenticationHelper authHelper;

    public OwnershipPolicy(AuthenticationHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Override
    public PolicyDecision evaluate(Authentication authentication, String action, ResourceContext context) {
        String ownerId = context.getStringAttribute("ownerId");
        if (ownerId == null) {
            return PolicyDecision.allow(); // No owner constraint in context
        }

        String subject = authHelper.getSub();
        if (ownerId.equals(subject)) {
            return PolicyDecision.allow();
        }

        return PolicyDecision.deny("Subject " + subject + " does not own resource owned by " + ownerId);
    }
}
