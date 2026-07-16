package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a department is edited. Catalog: "Edit department".
 * {@code previousValue}/{@code newValue} carry the compact field diff captured
 * before the update.
 */
public record DepartmentEditedEvent(String departmentName, String previousValue, String newValue)
        implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.ACCESS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.DEPARTMENT;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.EDIT;
    }

    @Override
    public String entityName() {
        return departmentName;
    }
}
