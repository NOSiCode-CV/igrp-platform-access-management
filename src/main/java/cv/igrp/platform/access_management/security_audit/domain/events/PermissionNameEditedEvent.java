package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when an administrator edits a permission's display name or
 * description (#12). {@code entityName} = key, {@code relatedEntity} =
 * service, previous and new values = {@link #label(String, String)}.
 */
public record PermissionNameEditedEvent(String key, String service, String previousLabel, String newLabel)
        implements SettingsAuditEvent {

    /** Name and description as one audit value. */
    public static String label(String name, String description) {
        return description == null ? name : name + " - " + description;
    }

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
        return key;
    }

    @Override
    public String relatedEntity() {
        return service;
    }

    @Override
    public String previousValue() {
        return previousLabel;
    }

    @Override
    public String newValue() {
        return newLabel;
    }
}
