package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a pending user invitation is cancelled. Catalog: "Cancel invite".
 *
 * <p>TODO(catalog-gap): no publisher wired in Phase 3. See {@code roadmap.md}.
 */
public record UserInviteCancelledEvent(String username) implements SettingsAuditEvent {

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
        return SettingsOperation.CANCEL_INVITE;
    }

    @Override
    public String entityName() {
        return username;
    }
}
