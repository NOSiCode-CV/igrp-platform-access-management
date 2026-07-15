package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Administrative area a settings/configuration event belongs to
 * ({@code t_security_audit_log.settings_area}). Backs the Settings Report
 * {@code area} filter and column (see catalog in {@code requirements.md} §1.6).
 */
public enum SettingsArea {
    APPLICATIONS,
    USERS,
    ACCESS
}
