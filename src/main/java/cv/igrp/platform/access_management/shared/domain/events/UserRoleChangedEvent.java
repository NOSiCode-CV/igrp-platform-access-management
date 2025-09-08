package cv.igrp.platform.access_management.shared.domain.events;

import org.springframework.context.ApplicationEvent;

public class UserRoleChangedEvent extends ApplicationEvent {

    private final String username;

    public UserRoleChangedEvent(Object source, String username) {
        super(source);
        this.username = username;
    }

    public String username() {
        return username;
    }
}
