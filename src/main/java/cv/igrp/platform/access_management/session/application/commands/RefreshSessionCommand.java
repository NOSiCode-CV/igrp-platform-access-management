package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class RefreshSessionCommand implements Command {
    private final String userId;
    private final Integer extensionSeconds;

    public RefreshSessionCommand(String userId, Integer extensionSeconds) {
        this.userId = userId;
        this.extensionSeconds = extensionSeconds;
    }
}
