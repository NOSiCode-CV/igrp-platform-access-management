package cv.igrp.platform.access_management.shared.domain.events;

import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
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
}
