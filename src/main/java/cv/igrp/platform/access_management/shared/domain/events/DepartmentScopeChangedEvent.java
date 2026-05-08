package cv.igrp.platform.access_management.shared.domain.events;

import lombok.Data;

import java.time.Instant;

/**
 * Event fired when a department's scope changes in a way that may invalidate
 * the permissions/roles its users currently hold (status flipping to
 * INACTIVE/DELETED, attached permissions/applications/menus/resources changing).
 * <p>
 * Used by Phase D9 to invalidate every active session for users belonging to
 * the affected department.
 */
@Data
public class DepartmentScopeChangedEvent {

    public static final String CHANGE_STATUS = "STATUS_CHANGED";
    public static final String CHANGE_PERMISSIONS = "PERMISSIONS_CHANGED";
    public static final String CHANGE_APPLICATIONS = "APPLICATIONS_CHANGED";
    public static final String CHANGE_MENUS = "MENUS_CHANGED";
    public static final String CHANGE_RESOURCES = "RESOURCES_CHANGED";

    private final String departmentCode;
    private final String changeType;
    private final String triggeredBy;
    private final Instant timestamp;

    public DepartmentScopeChangedEvent(String departmentCode, String changeType, String triggeredBy) {
        this.departmentCode = departmentCode;
        this.changeType = changeType;
        this.triggeredBy = triggeredBy;
        this.timestamp = Instant.now();
    }
}
