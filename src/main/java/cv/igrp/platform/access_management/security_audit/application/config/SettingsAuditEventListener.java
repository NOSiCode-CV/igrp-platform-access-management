package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Single sink for every {@link SettingsAuditEvent} published by the platform's
 * administrative command handlers (Phase 3 of the Unified Audit &amp; Reports
 * feature). Each concrete event maps one catalog-3.1 action; this listener
 * unpacks its typed fields and writes exactly one row to the unified audit log
 * via {@link SecurityAuditService#logSettingsEvent}.
 *
 * <p>Runs synchronously, on the publisher's thread and inside its transaction
 * (v3-23): the audit row commits with the change it records, or not at all. A
 * failed write propagates, so the change rolls back rather than going
 * unrecorded.
 */
@Component
public class SettingsAuditEventListener {

    private final SecurityAuditService securityAuditService;

    public SettingsAuditEventListener(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @EventListener
    public void onSettingsAuditEvent(SettingsAuditEvent event) {
        securityAuditService.logSettingsEvent(
                event.area(),
                event.entityType(),
                event.operation(),
                event.entityName(),
                event.relatedEntity(),
                event.previousValue(),
                event.newValue(),
                event.status());
    }
}
