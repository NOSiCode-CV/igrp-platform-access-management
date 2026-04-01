package cv.igrp.platform.access_management.session.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for session-related domain events
 */
public abstract class SessionEvent {
    private final UUID sessionId;
    private final String userExternalId;
    private final Instant timestamp;
    private final String source;

    protected SessionEvent(UUID sessionId, String userExternalId, String source) {
        this.sessionId = sessionId;
        this.userExternalId = userExternalId;
        this.timestamp = Instant.now();
        this.source = source;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "SessionEvent{" +
                "sessionId=" + sessionId +
                ", userExternalId='" + userExternalId + '\'' +
                ", timestamp=" + timestamp +
                ", source='" + source + '\'' +
                '}';
    }
}
