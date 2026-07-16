package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
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
     * Logs a security event, attaching report-facing detail for the Audit and
     * Access reports (requirements.md §1.5.1 / §1.5.2).
     *
     * <p>Columns the caller leaves unset on {@code report} are derived from the
     * event and the ambient request context where that can be done honestly —
     * see {@link AuditReportContext}.
     *
     * @param type      The type of the event.
     * @param category  The category of the event.
     * @param context   A map of contextual data related to the event.
     * @param report    Report columns to record alongside the event.
     */
    void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context,
                  AuditReportContext report);

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
     * Logs an access denied event with an explicit decision reason.
     *
     * @param permission The permission that was denied.
     * @param reason     The reason why access was denied.
     */
    void logAccessDenied(String permission, String reason);

    /**
     * Logs a change to a user account.
     *
     * @param targetUserId The ID of the user account that was changed.
     * @param operation    The operation performed on the user account (e.g., "CREATE", "UPDATE", "DELETE").
     */
    void logUserChange(String targetUserId, String operation);

    /**
     * Logs an administrative configuration (settings) event onto the unified
     * audit log with its typed report columns populated (Settings Report,
     * requirements.md §1.5.3). Fail-safe: never rethrows. Status defaults to
     * {@link AuditStatus#SUCCESS}.
     *
     * @param area          administrative area.
     * @param entityType    type of the target entity.
     * @param operation     operation performed.
     * @param entityName    name of the target entity.
     * @param relatedEntity related entity for associations/assignments (nullable).
     * @param previousValue previous value on EDIT (nullable).
     * @param newValue      new value on EDIT (nullable).
     */
    void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                          String entityName, String relatedEntity, String previousValue, String newValue);

    /**
     * Settings event variant with an explicit outcome status.
     */
    void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                          String entityName, String relatedEntity, String previousValue, String newValue,
                          AuditStatus status);
}