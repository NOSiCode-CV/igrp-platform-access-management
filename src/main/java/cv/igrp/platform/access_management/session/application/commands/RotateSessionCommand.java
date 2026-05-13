package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class RotateSessionCommand implements Command {
    private final String userId;
    private final String clientIp;
    private final String userAgent;
    private final String deviceId;

    public RotateSessionCommand(String userId, String clientIp, String userAgent, String deviceId) {
        this.userId = userId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
    }
}
