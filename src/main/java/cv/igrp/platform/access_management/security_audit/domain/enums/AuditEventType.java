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

    // Session lifecycle (NFR-4) — every state change emits one row.
    SESSION_CREATED,
    SESSION_REPLACED,
    SESSION_LIMIT_EXCEEDED,
    SESSION_REFRESHED,
    SESSION_REVOKED,
    SESSION_EXPIRED,
    SESSION_FORCED_REAUTH,

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
    /** A user's permissions were computed for an application: the user entered it (v3, emitted by #14). */
    APPLICATION_ACCESSED,
    /** The row-visibility scope of a role permission changed (v3, emitted by #21). */
    SCOPE_CHANGED,
    /** Root (super-admin) moved to another user (v3, emitted by #20). */
    ROOT_CHANGED,

    // System-level Events
    SYSTEM_CONFIGURATION_CHANGED,
    /** Retention archived a run of audit rows to storage before deleting them. */
    AUDIT_ARCHIVED
}