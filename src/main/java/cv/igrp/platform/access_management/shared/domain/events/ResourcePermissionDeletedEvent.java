package cv.igrp.platform.access_management.shared.domain.events;

import org.springframework.context.ApplicationEvent;

public class ResourcePermissionDeletedEvent extends ApplicationEvent {
    private final String resourceName;

    public ResourcePermissionDeletedEvent(Object source, String resourceName) {
        super(source);
        this.resourceName = resourceName;
    }

    public String resourceName() {
        return resourceName;
    }
}

