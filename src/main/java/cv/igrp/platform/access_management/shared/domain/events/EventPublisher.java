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

    public void publishPermissionDeleted(DeletePermissionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void publishDepartmentScopeChanged(DepartmentScopeChangedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
