package cv.igrp.platform.access_management.shared.domain.events;

import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public EventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes an event in the Spring context.
     *
     * @param event the event that will be published
     * @param <T> the event type
     */
    public <T> void publish(T event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publishUserRoleChanged(UserRoleChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publishRolePermissionChanged(RolePermissionChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publishUserStatusChanged(UserStatusChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * Cascade trigger — FR-16. MUST be called by every code path that removes
     * a permission row (or soft-deletes it via {@code Status.DELETED}),
     * immediately after the DB write commits. The matching listeners
     * ({@link cv.igrp.platform.access_management.session.application.listener.SessionInvalidationEventListener}
     * and {@link cv.igrp.platform.access_management.shared.infrastructure.cache.PermissionCacheInvalidator})
     * resolve the affected users and (a) revoke their server-side sessions
     * and (b) evict their permission-cache entries.
     * <p>
     * Wired today by:
     * <ul>
     *   <li>{@code m2m.domain.service.PermissionSyncService#synchronizePermissions}
     *       — soft-deletes permissions absent from the incoming sync payload.</li>
     * </ul>
     * Any future "delete permission" command handler or admin endpoint MUST
     * also call this — failing to do so leaves users authenticated via cached
     * JWTs / cached permissions for a permission the system no longer recognises.
     */
    public void publishPermissionDeleted(DeletePermissionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publishDepartmentScopeChanged(DepartmentScopeChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * Publishes a strongly-typed administrative "settings" event (Phase 3 of the
     * Unified Audit &amp; Reports feature). Consumed asynchronously by
     * {@code SettingsAuditEventListener}, which writes one row to the unified audit
     * log for the Settings Report. Call after the command's DB write succeeds,
     * before returning. Publishing is fire-and-forget and must never affect the
     * caller's outcome.
     */
    public void publishSettingsAudit(SettingsAuditEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
