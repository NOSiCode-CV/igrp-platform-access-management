package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when an application is edited. Catalog: "Edit application".
 * {@code previousValue}/{@code newValue} carry the compact field diff captured
 * before the update (e.g. {@code "name=old→new; status=ACTIVE→INACTIVE"}).
 */
public record ApplicationEditedEvent(String applicationName, String previousValue, String newValue)
        implements SettingsAuditEvent {

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
        return SettingsOperation.EDIT;
    }

    @Override
    public String entityName() {
        return applicationName;
    }
}
