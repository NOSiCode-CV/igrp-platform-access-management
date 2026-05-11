package cv.igrp.platform.access_management.session.domain.event;

import java.util.UUID;

/**
 * Event fired when a session is revoked (admin action or security reason)
 */
public class SessionRevokedEvent extends SessionEvent {

    private final String reason;
    private final String revokedBy;

    public SessionRevokedEvent(UUID sessionId, Integer userId, String reason, String revokedBy) {
        super(sessionId, userId, "SESSION_SECURITY");
        this.reason = reason;
        this.revokedBy = revokedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    @Override
    public String toString() {
        return "SessionRevokedEvent{" +
                "sessionId=" + getSessionId() +
                ", userId='" + getUserId() + '\'' +
                ", reason='" + reason + '\'' +
                ", revokedBy='" + revokedBy + '\'' +
                ", timestamp=" + getTimestamp() +
                ", source='" + getSource() + '\'' +
                '}';
    }
}
