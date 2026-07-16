package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when a department is created. Catalog: "Create department". */
public record DepartmentCreatedEvent(String departmentName) implements SettingsAuditEvent {

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
        return SettingsOperation.CREATE;
    }

    @Override
    public String entityName() {
        return departmentName;
    }
}
