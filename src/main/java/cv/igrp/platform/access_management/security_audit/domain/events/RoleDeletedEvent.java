package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when a role is removed (soft delete). Catalog: "Remove role". */
public record RoleDeletedEvent(String roleName) implements SettingsAuditEvent {

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
        return SettingsOperation.DELETE;
    }

    @Override
    public String entityName() {
        return roleName;
    }
}
