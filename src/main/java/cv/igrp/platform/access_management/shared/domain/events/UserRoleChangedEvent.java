package cv.igrp.platform.access_management.shared.domain.events;

import lombok.Data;

import java.time.Instant;
import java.util.Set;

/**
 * Canonical event fired when a user's role membership changes.
 *
 * Consumed by:
 * - SessionInvalidationEventListener (invalidates server-side sessions)
 * - PermissionCacheInvalidator (evicts permission cache entries)
 */
@Data
public class UserRoleChangedEvent {

    public static final String CHANGE_ADDED = "ADDED";
    public static final String CHANGE_REMOVED = "REMOVED";
    public static final String CHANGE_ACTIVE_ROLE_CHANGED = "ACTIVE_ROLE_CHANGED";

    private final String userId;
    private final Set<String> affectedRoleCodes;
    private final String departmentCode;
    private final String changeType;
    private final String triggeredBy;
    private final Instant timestamp;

    public UserRoleChangedEvent(String userId,
                                Set<String> affectedRoleCodes,
                                String departmentCode,
                                String changeType,
                                String triggeredBy) {
        this.userId = userId;
        this.affectedRoleCodes = affectedRoleCodes;
        this.departmentCode = departmentCode;
        this.changeType = changeType;
        this.triggeredBy = triggeredBy;
        this.timestamp = Instant.now();
    }
}
