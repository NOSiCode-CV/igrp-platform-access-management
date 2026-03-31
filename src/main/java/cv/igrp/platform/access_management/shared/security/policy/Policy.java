package cv.igrp.platform.access_management.shared.security.policy;

import org.springframework.security.core.Authentication;

/**
 * Interface for policy-based authorization (ABAC).
 */
public interface Policy {
    /**
     * Evaluates the policy against the given authentication and resource context.
     *
     * @param authentication the current authentication
     * @param action         the action being performed (e.g., "users.update")
     * @param context        the context of the resource being accessed
     * @return the result of the evaluation
     */
    PolicyDecision evaluate(Authentication authentication, String action, ResourceContext context);
}
