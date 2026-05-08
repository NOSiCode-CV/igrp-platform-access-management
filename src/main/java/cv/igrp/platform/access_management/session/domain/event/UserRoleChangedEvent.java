package cv.igrp.platform.access_management.session.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Set;

/**
 * Event fired when a user's role membership changes
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRoleChangedEvent extends SessionEvent {

    private Integer userId;
    private Set<String> affectedRoleCodes;
    private String departmentCode;
    private String changeType; // ADDED, REMOVED, ACTIVE_ROLE_CHANGED
    private Instant timestamp;

    public UserRoleChangedEvent(Integer userId, Set<String> affectedRoleCodes,
                                String departmentCode, String changeType, String triggeredBy) {
        super(null, userId, triggeredBy);
        this.userId = userId;
        this.affectedRoleCodes = affectedRoleCodes;
        this.departmentCode = departmentCode;
        this.changeType = changeType;
        this.timestamp = Instant.now();
    }
}
