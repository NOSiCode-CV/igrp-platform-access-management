package cv.igrp.platform.access_management.session.domain.event;

import java.util.UUID;

/**
 * Event fired when a new session is created
 */
public class SessionCreatedEvent extends SessionEvent {

    private final String clientIp;
    private final String deviceId;

    public SessionCreatedEvent(UUID sessionId, String userExternalId, String clientIp, String deviceId) {
        super(sessionId, userExternalId, "SESSION_MANAGEMENT");
        this.clientIp = clientIp;
        this.deviceId = deviceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String toString() {
        return "SessionCreatedEvent{" +
                "sessionId=" + getSessionId() +
                ", userExternalId='" + getUserExternalId() + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", timestamp=" + getTimestamp() +
                ", source='" + getSource() + '\'' +
                '}';
    }
}
