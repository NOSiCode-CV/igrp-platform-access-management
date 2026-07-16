package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Operation performed by a settings/configuration event
 * ({@code t_security_audit_log.settings_operation}). Backs the Settings Report
 * {@code operation} filter and column.
 *
 * <p>Symmetry rule (see {@code requirements.md} §1.6): every additive operation
 * has its inverse — {@code CREATE↔DELETE}, {@code ACTIVATE↔DEACTIVATE},
 * {@code ASSOCIATE↔DISASSOCIATE}, {@code ASSIGN↔UNASSIGN}, {@code INVITE↔CANCEL_INVITE}.
 * {@code RESEND_INVITE} is repeatable and has no inverse.
 */
public enum SettingsOperation {
    CREATE,
    DELETE,
    EDIT,
    ACTIVATE,
    DEACTIVATE,
    INVITE,
    CANCEL_INVITE,
    RESEND_INVITE,
    ASSOCIATE,
    DISASSOCIATE,
    ASSIGN,
    UNASSIGN
}
