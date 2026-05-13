package cv.igrp.platform.access_management.session.domain.event;

import java.util.UUID;

/**
 * Event fired when a session expires
 */
public class SessionExpiredEvent extends SessionEvent {

    public SessionExpiredEvent(UUID sessionId, String userId) {
        super(sessionId, userId, "SESSION_TIMEOUT");
    }

    @Override
    public String toString() {
        return "SessionExpiredEvent{" +
                "sessionId=" + getSessionId() +
                ", userId='" + getUserId() + '\'' +
                ", timestamp=" + getTimestamp() +
                ", source='" + getSource() + '\'' +
                '}';
    }
}
