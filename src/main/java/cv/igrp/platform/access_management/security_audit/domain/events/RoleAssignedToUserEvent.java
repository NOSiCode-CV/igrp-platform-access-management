package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a role is assigned to a user.
 * Catalog: "Assign role to user". {@code relatedEntity} = target user.
 */
public record RoleAssignedToUserEvent(String roleCode, String username)
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
        return SettingsOperation.ASSIGN;
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
