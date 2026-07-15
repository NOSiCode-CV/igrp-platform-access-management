package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a role is unassigned from a user.
 * Catalog: "Unassign role from user". {@code relatedEntity} = target user.
 */
public record RoleUnassignedFromUserEvent(String roleCode, String username)
        implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.ACCESS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.ROLE;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.UNASSIGN;
    }

    @Override
    public String entityName() {
        return roleCode;
    }

    @Override
    public String relatedEntity() {
        return username;
    }
}
