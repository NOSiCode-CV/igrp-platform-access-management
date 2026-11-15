package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when an administrator confirms a migration review suggestion (#37):
 * the legacy permission's roles and departments were copied to the route key.
 * {@code entityName} = key, {@code relatedEntity} = legacy permission.
 */
public record LegacyAssignmentsCopiedEvent(String key, String legacyName, int roles, int departments)
        implements SettingsAuditEvent {

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
        return SettingsOperation.ASSOCIATE;
    }

    @Override
    public String entityName() {
        return key;
    }

    @Override
    public String relatedEntity() {
        return legacyName;
    }

    @Override
    public String previousValue() {
        return null;
    }

    @Override
    public String newValue() {
        return "roles=" + roles + ", departments=" + departments;
    }
}
