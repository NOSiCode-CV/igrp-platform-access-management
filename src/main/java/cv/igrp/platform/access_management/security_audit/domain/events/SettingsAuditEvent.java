package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Marker interface for strongly-typed administrative "settings" events published
 * by the platform's command handlers (Phase 3 of the Unified Audit &amp; Reports
 * feature).
 *
 * <p>Every catalog-3.1 action (see {@code requirements.md} §1.6 and the mapping
 * table in {@code plan.md} §Phase 3) publishes a concrete event implementing this
 * interface. A single
 * {@link cv.igrp.platform.access_management.security_audit.application.config.SettingsAuditEventListener}
 * consumes them all and writes one row to the unified audit log via
 * {@code SecurityAuditService.logSettingsEvent(...)}, populating the Settings
 * Report typed columns.
 *
 * <p>Implementations are records: the operation-defining fields
 * ({@link #area()}, {@link #entityType()}, {@link #operation()}) are constants
 * per event class, while the free-text fields ({@link #entityName()},
 * {@link #relatedEntity()}, {@link #previousValue()}, {@link #newValue()}) carry
 * the runtime payload. Nullable fields default to {@code null} and the outcome
 * defaults to {@link AuditStatus#SUCCESS}.
 */
public interface SettingsAuditEvent {

    /** Administrative area the event belongs to. */
    SettingsArea area();

    /** Type of entity the event targets. */
    SettingsEntityType entityType();

    /** Operation performed. */
    SettingsOperation operation();

    /** Human-readable name/code of the target entity. */
    String entityName();

    /**
     * Related entity for {@code ASSOCIATE}/{@code DISASSOCIATE}/{@code ASSIGN}/
     * {@code UNASSIGN} events (e.g. the role a permission is attached to). Null
     * for simple CRUD events.
     */
    default String relatedEntity() {
        return null;
    }

    /** Compact representation of the pre-change value(s) on {@code EDIT}. Null otherwise. */
    default String previousValue() {
        return null;
    }

    /** Compact representation of the post-change value(s) on {@code EDIT}. Null otherwise. */
    default String newValue() {
        return null;
    }

    /** Outcome of the operation. Defaults to {@link AuditStatus#SUCCESS}. */
    default AuditStatus status() {
        return AuditStatus.SUCCESS;
    }
}
