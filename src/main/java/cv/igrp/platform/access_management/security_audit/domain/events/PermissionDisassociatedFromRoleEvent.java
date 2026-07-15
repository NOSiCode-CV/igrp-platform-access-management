package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a permission is removed from a role.
 * Catalog: "Remove permission from role". {@code relatedEntity} = role code.
 */
public record PermissionDisassociatedFromRoleEvent(String permissionName, String roleCode)
        implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.ACCESS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.PERMISSION;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.DISASSOCIATE;
    }

    @Override
    public String entityName() {
        return permissionName;
    }

    @Override
    public String relatedEntity() {
        return roleCode;
    }
}
