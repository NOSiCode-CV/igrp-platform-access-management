package cv.igrp.platform.access_management.shared.domain.events;

import org.springframework.context.ApplicationEvent;

public class DeletePermissionEvent extends ApplicationEvent {

    private final String permissionName;

    public DeletePermissionEvent(Object source,  String permissionName) {
        super(source);
        this.permissionName = permissionName;
    }

    public String permissionName() {
        return permissionName;
    }
}
