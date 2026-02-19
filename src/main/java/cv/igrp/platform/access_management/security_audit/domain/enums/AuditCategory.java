package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Enum for categorizing security audit events into broad functional areas.
 * This allows for high-level filtering and reporting on security-related activities.
 */
public enum AuditCategory {
    /**
     * Events related to user authentication, such as login, logout, and session management.
     */
    AUTHENTICATION,

    /**
     * Events related to access control decisions, including granting or denying access to resources.
     */
    AUTHORIZATION,

    /**
     * Events related to user session lifecycle.
     */
    SESSION,

    /**
     * Events related to changes in user privileges, roles, and permissions.
     */
    PRIVILEGE,

    /**
     * Events related to user account management (creation, modification, deletion).
     */
    USER_MANAGEMENT,

    /**
     * Events related to system-wide configuration changes or critical system operations.
     */
    SYSTEM
}