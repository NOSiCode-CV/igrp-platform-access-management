package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when the scope of a permission in a role changes (#21).
 * {@code entityName} = permission key, {@code relatedEntity} = role code.
 */
public record PermissionScopeChangedEvent(String permissionKey, String roleCode, String previousValue,
                                          String newValue) implements SettingsAuditEvent {

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
        return SettingsOperation.EDIT;
    }

    @Override
    public String entityName() {
        return permissionKey;
    }

    @Override
    public String relatedEntity() {
        return roleCode;
    }
}
