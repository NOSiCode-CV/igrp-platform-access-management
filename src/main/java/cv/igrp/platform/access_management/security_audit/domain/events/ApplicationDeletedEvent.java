package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when an application is removed (soft delete). Catalog: "Remove application". */
public record ApplicationDeletedEvent(String applicationName) implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.APPLICATIONS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.APPLICATION;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.DELETE;
    }

    @Override
    public String entityName() {
        return applicationName;
    }
}
