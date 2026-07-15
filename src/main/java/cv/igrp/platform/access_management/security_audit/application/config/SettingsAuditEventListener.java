package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Single sink for every {@link SettingsAuditEvent} published by the platform's
 * administrative command handlers (Phase 3 of the Unified Audit &amp; Reports
 * feature). Each concrete event maps one catalog-3.1 action; this listener
 * unpacks its typed fields and writes exactly one row to the unified audit log
 * via {@link SecurityAuditService#logSettingsEvent}.
 *
 * <p>Runs on the dedicated {@code settingsAuditExecutor} (see
 * {@link SettingsAuditAsyncConfig}) so it never blocks the triggering action.
 * The executor's task decorator re-binds the request-thread audit context, so the
 * resulting row still carries the correct IP, user agent and acting user. The
 * write itself is fail-safe in {@code SecurityAuditServiceImpl} (N4) — nothing
 * thrown here can propagate back to the admin command, which has already
 * completed and returned.
 */
@Component
public class SettingsAuditEventListener {

    private static final Logger logger = LoggerFactory.getLogger(SettingsAuditEventListener.class);

    private final SecurityAuditService securityAuditService;

    public SettingsAuditEventListener(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @Async(SettingsAuditAsyncConfig.EXECUTOR_BEAN)
    @EventListener
    public void onSettingsAuditEvent(SettingsAuditEvent event) {
        try {
            securityAuditService.logSettingsEvent(
                    event.area(),
                    event.entityType(),
                    event.operation(),
                    event.entityName(),
                    event.relatedEntity(),
                    event.previousValue(),
                    event.newValue(),
                    event.status());
        } catch (Exception e) {
            // Defence in depth — the service is already fail-safe, but guarantee the
            // async worker never surfaces an exception to the executor (N4).
            logger.error("[Security audit] Failed to handle settings audit event {}",
                    event.getClass().getSimpleName(), e);
        }
    }
}
