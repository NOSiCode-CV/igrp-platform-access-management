package cv.igrp.platform.access_management.shared.domain.events;

import lombok.Data;

import java.time.Instant;
import java.util.Set;

/**
 * Canonical event fired when a user's role membership changes.
 *
 * Consumed by:
 * - SessionInvalidationEventListener (invalidates server-side sessions)
 * - PermissionSetEviction (deletes the user's permission sets after commit)
 */
@Data
public class UserRoleChangedEvent {

    public static final String CHANGE_ADDED = "ADDED";
    public static final String CHANGE_REMOVED = "REMOVED";
    public static final String CHANGE_ACTIVE_ROLE_CHANGED = "ACTIVE_ROLE_CHANGED";
    /**
     * A temporary assignment passed its {@code expires_at} and was reaped by
     * {@code ExpireRoleService}. Distinct from {@link #CHANGE_REMOVED} only so
     * logs and audit trails can tell an administrator's revocation from the
     * clock running out; both listeners treat them identically.
     */
    public static final String CHANGE_EXPIRED = "EXPIRED";

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
