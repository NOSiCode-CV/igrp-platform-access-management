package cv.igrp.platform.access_management.security_audit.domain.enums;

/**
 * Outcome of an audited operation, surfaced on the report {@code status} column.
 *
 * <p>Populated on the {@code t_security_audit_log.status} column (Phase 2). Phase 1
 * auth rows leave it {@code null}; Phase 3 instrumentation fills it for settings
 * events. The three report DTOs each expose a documented subset of these values
 * (see {@code requirements.md} §1.5).
 */
public enum AuditStatus {
    SUCCESS,
    ACCESS_DENIED,
    UNUSUAL_IP,
    PENDING,
    ERROR
}
