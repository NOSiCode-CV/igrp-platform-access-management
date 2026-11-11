package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a service's route registration creates a permission (v3-02).
 * {@code entityName} = the route key, {@code relatedEntity} = the service.
 */
public record PermissionRegisteredEvent(String key, String service) implements SettingsAuditEvent {

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
        return SettingsOperation.CREATE;
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
