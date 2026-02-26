package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import java.util.Map;

/**
 * Service interface for logging security-related audit events.
 * This provides a centralized mechanism for recording auditable actions within the application.
 */
public interface SecurityAuditService {

    /**
     * Generic method to log a security event.
     *
     * @param type      The type of the event.
     * @param category  The category of the event.
     * @param context   A map of contextual data related to the event.
     */
    void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context);

    /**
     * Logs a successful authentication event.
     */
    void logAuthenticationSuccess();

    /**
     * Logs a failed authentication attempt.
     *
     * @param reason The reason for the authentication failure.
     */
    void logAuthenticationFailure(String reason);

    /**
     * Logs a profile switch event.
     *
     * @param oldRole The user's previous role or profile.
     * @param newRole The user's new role or profile.
     */
    void logProfileSwitch(Integer oldRole, Integer newRole);

    /**
     * Logs an access denied event.
     *
     * @param permission The permission that was denied.
     */
    void logAccessDenied(String permission);

    /**
     * Logs a change to a user account.
     *
     * @param targetUserId The ID of the user account that was changed.
     * @param operation    The operation performed on the user account (e.g., "CREATE", "UPDATE", "DELETE").
     */
    void logUserChange(String targetUserId, String operation);
}