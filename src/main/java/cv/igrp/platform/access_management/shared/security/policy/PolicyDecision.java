package cv.igrp.platform.access_management.shared.security.policy;

/**
 * Represents the result of a policy evaluation.
 */
public record PolicyDecision(boolean allowed, String reason) {
    public static PolicyDecision allow() {
        return new PolicyDecision(true, "Allowed by policy");
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }
}
