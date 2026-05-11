package cv.igrp.platform.access_management.session.domain.event;

import java.util.UUID;

/**
 * Event fired when a session is closed
 */
public class SessionClosedEvent extends SessionEvent {

    private final String reason;
    private final String closedBy;

    public SessionClosedEvent(UUID sessionId, Integer userId, String reason, String closedBy) {
        super(sessionId, userId, "SESSION_MANAGEMENT");
        this.reason = reason;
        this.closedBy = closedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getClosedBy() {
        return closedBy;
    }

    @Override
    public String toString() {
        return "SessionClosedEvent{" +
                "sessionId=" + getSessionId() +
                ", userId='" + getUserId() + '\'' +
                ", reason='" + reason + '\'' +
                ", closedBy='" + closedBy + '\'' +
                ", timestamp=" + getTimestamp() +
                ", source='" + getSource() + '\'' +
                '}';
    }
}
