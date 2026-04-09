package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class CloseSessionCommand implements Command {
    private final String userExternalId;
    private final String reason;
    
    public CloseSessionCommand(String userExternalId, String reason) {
        this.userExternalId = userExternalId;
        this.reason = reason;
    }
}
