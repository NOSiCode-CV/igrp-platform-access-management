package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class RefreshSessionCommand implements Command {
    private final String userExternalId;
    private final Integer extensionSeconds;
    
    public RefreshSessionCommand(String userExternalId, Integer extensionSeconds) {
        this.userExternalId = userExternalId;
        this.extensionSeconds = extensionSeconds;
    }
}
