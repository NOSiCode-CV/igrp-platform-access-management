package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Type of entity a settings/configuration event targets
 * ({@code t_security_audit_log.settings_entity_type}). Backs the Settings Report
 * {@code entityType} filter and column.
 */
public enum SettingsEntityType {
    APPLICATION,
    USER,
    DEPARTMENT,
    ROLE,
    PERMISSION,
    MENU
}
