package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when an application is revoked from a department (access scope).
 * Catalog: "Disassociate application from role". {@code relatedEntity} = department code.
 */
public record ApplicationDisassociatedFromRoleEvent(String applicationName, String departmentCode)
        implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.ACCESS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.APPLICATION;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.DISASSOCIATE;
    }

    @Override
    public String entityName() {
        return applicationName;
    }

    @Override
    public String relatedEntity() {
        return departmentCode;
    }
}
