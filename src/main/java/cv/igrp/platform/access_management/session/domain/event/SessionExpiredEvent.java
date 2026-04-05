package cv.igrp.platform.access_management.session.domain.event;

import java.util.UUID;

/**
 * Event fired when a session expires
 */
public class SessionExpiredEvent extends SessionEvent {

    public SessionExpiredEvent(UUID sessionId, String userExternalId) {
        super(sessionId, userExternalId, "SESSION_TIMEOUT");
    }

    @Override
    public String toString() {
        return "SessionExpiredEvent{" +
                "sessionId=" + getSessionId() +
                ", userExternalId='" + getUserExternalId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", source='" + getSource() + '\'' +
                '}';
    }
}
