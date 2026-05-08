package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class CloseSessionCommand implements Command {
    private final Integer userId;
    private final String reason;

    public CloseSessionCommand(Integer userId, String reason) {
        this.userId = userId;
        this.reason = reason;
    }
}
