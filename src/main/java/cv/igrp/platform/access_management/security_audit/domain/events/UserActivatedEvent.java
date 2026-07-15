package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a user is activated. Catalog: "Activate user".
 *
 * <p>TODO(catalog-gap): no publisher wired in Phase 3 — activation is currently
 * folded into {@code UpdateUserStatusCommandHandler}. See {@code roadmap.md}.
 */
public record UserActivatedEvent(String username) implements SettingsAuditEvent {

    @Override
    public SettingsArea area() {
        return SettingsArea.USERS;
    }

    @Override
    public SettingsEntityType entityType() {
        return SettingsEntityType.USER;
    }

    @Override
    public SettingsOperation operation() {
        return SettingsOperation.ACTIVATE;
    }

    @Override
    public String entityName() {
        return username;
    }
}
