package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/** Published when a service rename rewrites a key (#11). {@code entityName} = new key, {@code relatedEntity} = service, previous and new values = the keys. */
public record PermissionKeyRenamedEvent(String previousKey, String newKey, String service) implements SettingsAuditEvent {

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
        return newKey;
    }

    @Override
    public String relatedEntity() {
        return service;
    }

    @Override
    public String previousValue() {
        return previousKey;
    }

    @Override
    public String newValue() {
        return newKey;
    }
}
