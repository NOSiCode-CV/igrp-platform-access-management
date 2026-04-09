package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

import java.util.UUID;

@Getter
public class KillSessionCommand implements Command {
    private final java.util.UUID sessionId;
    private final String reason;
    private final String killedBy;
    
    public KillSessionCommand(java.util.UUID sessionId, String reason, String killedBy) {
        this.sessionId = sessionId;
        this.reason = reason;
        this.killedBy = killedBy;
    }
}
