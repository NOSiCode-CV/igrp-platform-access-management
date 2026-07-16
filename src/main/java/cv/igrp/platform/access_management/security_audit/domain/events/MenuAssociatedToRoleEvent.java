package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a menu entry is granted to a department (access scope).
 * Catalog: "Associate menu to role". {@code relatedEntity} = department code.
 */
public record MenuAssociatedToRoleEvent(String menuName, String departmentCode)
        implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.ACCESS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.MENU;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.ASSOCIATE;
    }

    @Override
    public String entityName() {
        return menuName;
    }

    @Override
    public String relatedEntity() {
        return departmentCode;
    }
}
