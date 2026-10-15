package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RotateSessionCommand implements Command {
    private final String userId;
    /**
     * The caller's own session, from the {@code sid} claim of their access
     * token. Required to tell WHICH session to close and replace: without it
     * rotation closed the user's most recently seen rows, which with several
     * applications per browser meant signing siblings out.
     * Null only for tokens issued without a sid.
     */
    private final UUID sid;
    private final String clientIp;
    private final String userAgent;
    private final String deviceId;

    public RotateSessionCommand(String userId, UUID sid, String clientIp, String userAgent, String deviceId) {
        this.userId = userId;
        this.sid = sid;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
    }
}
