package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when a route key is deleted after its service stopped declaring it (#11). {@code entityName} = key, {@code relatedEntity} = service. */
public record PermissionDeletedEvent(String key, String service) implements SettingsAuditEvent {

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
        return SettingsOperation.DELETE;
    }

    @Override
    public String entityName() {
        return key;
    }

    @Override
    public String relatedEntity() {
        return service;
    }
}
