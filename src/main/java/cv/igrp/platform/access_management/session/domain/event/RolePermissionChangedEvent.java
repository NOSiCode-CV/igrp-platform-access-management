package cv.igrp.platform.access_management.session.domain.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Event fired when role permissions change
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePermissionChangedEvent extends SessionEvent {

    private String roleCode;
    private String departmentCode;
    private String changeType; // PERMISSIONS_ADDED, PERMISSIONS_REMOVED, ROLE_STATUS_CHANGED
    private Instant timestamp;

    public RolePermissionChangedEvent(String roleCode, String departmentCode, 
                                      String changeType, String triggeredBy) {
        super(null, null, triggeredBy);
        this.roleCode = roleCode;
        this.departmentCode = departmentCode;
        this.changeType = changeType;
        this.timestamp = Instant.now();
    }
}
