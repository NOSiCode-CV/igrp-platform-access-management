package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * A user switched their active role in an application (v3-05), written in the
 * switch's transaction. Roles are labelled {@code department/role}, because
 * role codes are unique only within a department.
 *
 * @param role         the new role, {@code department/role}
 * @param application  the application code
 * @param previousRole the role the user was acting under before (stored or default), or null for none
 */
public record ActiveRoleSwitchedEvent(String role, String application, String previousRole)
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
        return SettingsOperation.ASSIGN;
    }

    @Override
    public String entityName() {
        return role;
    }

    @Override
    public String relatedEntity() {
        return application;
    }

    @Override
    public String previousValue() {
        return previousRole;
    }

    @Override
    public String newValue() {
        return role;
    }
}
