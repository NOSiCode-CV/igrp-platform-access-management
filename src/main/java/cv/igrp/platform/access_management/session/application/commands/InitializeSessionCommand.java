package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class InitializeSessionCommand implements Command {
    private final String userExternalId;
    private final String clientIp;
    private final String userAgent;
    private final String deviceId;
    
    public InitializeSessionCommand(String userExternalId, String clientIp, String userAgent, String deviceId) {
        this.userExternalId = userExternalId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
    }
}
