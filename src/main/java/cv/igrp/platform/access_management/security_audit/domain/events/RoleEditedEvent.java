package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a role is edited. Catalog: "Edit role".
 * {@code previousValue}/{@code newValue} carry the compact field diff captured
 * before the update.
 */
public record RoleEditedEvent(String roleName, String previousValue, String newValue)
        implements SettingsAuditEvent {

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
        return SettingsOperation.EDIT;
    }

    @Override
    public String entityName() {
        return roleName;
    }
}
