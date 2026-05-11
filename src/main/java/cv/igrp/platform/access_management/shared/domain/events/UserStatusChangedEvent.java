package cv.igrp.platform.access_management.shared.domain.events;

import lombok.Data;

import java.time.Instant;

/**
 * Event fired when a user's status changes (e.g. ACTIVE → SUSPENDED).
 * Used by Phase D5 to invalidate sessions on user-status mutation.
 */
@Data
public class UserStatusChangedEvent {

    private final Integer userId;
    private final String previousStatus;
    private final String newStatus;
    private final String triggeredBy;
    private final Instant timestamp;

    public UserStatusChangedEvent(Integer userId,
                                  String previousStatus,
                                  String newStatus,
                                  String triggeredBy) {
        this.userId = userId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.triggeredBy = triggeredBy;
        this.timestamp = Instant.now();
    }
}
