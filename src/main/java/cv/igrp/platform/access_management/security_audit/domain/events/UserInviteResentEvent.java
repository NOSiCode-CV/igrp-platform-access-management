package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when a user invitation is resent. Catalog: "Resend invite"
 * (repeatable, no inverse per the §1.6 symmetry rule).
 *
 * <p>TODO(catalog-gap): no publisher wired in Phase 3. See {@code roadmap.md}.
 */
public record UserInviteResentEvent(String username) implements SettingsAuditEvent {

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
        return SettingsOperation.RESEND_INVITE;
    }

    @Override
    public String entityName() {
        return username;
    }
}
