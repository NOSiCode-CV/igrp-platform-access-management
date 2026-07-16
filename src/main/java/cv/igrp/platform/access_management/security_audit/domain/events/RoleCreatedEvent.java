package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when a role is created. Catalog: "Create role". */
public record RoleCreatedEvent(String roleName) implements SettingsAuditEvent {

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
        return SettingsOperation.CREATE;
    }

    @Override
    public String entityName() {
        return roleName;
    }
}
