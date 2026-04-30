package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Enum representing the specific type of security event that occurred.
 * This provides a granular classification of audit trail entries, essential for
 * monitoring and forensic analysis.
 */
public enum AuditEventType {
    // Authentication Events
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_REFRESH,
    SESSION_EXPIRED,

    // Token / Auth
    TOKEN_ACCEPTED,
    TOKEN_REJECTED,
    TOKEN_EXPIRED,
    TOKEN_ISSUED,
    TOKEN_REVOKED,

    // User Management Events
    USER_CREATED,
    USER_UPDATED,
    USER_INACTIVATED,
    USER_ACTIVATED,
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,

    // Profile & Role Management Events
    PROFILE_ACTIVATED,
    PROFILE_DEACTIVATED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    ROLE_EXPIRED,

    // Permission & Authorization Events
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    ACCESS_GRANTED,
    ACCESS_DENIED,

    // System-level Events
    SYSTEM_CONFIGURATION_CHANGED
}