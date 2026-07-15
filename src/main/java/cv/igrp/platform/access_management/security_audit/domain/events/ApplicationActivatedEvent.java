package cv.igrp.platform.access_management.security_audit.domain.events;

import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;

/**
 * Published when an application is activated. Catalog: "Activate application".
 *
 * <p>TODO(catalog-gap): no publisher yet — activation is currently folded into
 * {@code UpdateApplicationCommandHandler}. The event class exists so the Settings
 * Report enum surface is complete; wiring awaits the dedicated activate command
 * (see {@code roadmap.md} "Activate / Deactivate / Invite command handlers").
 */
public record ApplicationActivatedEvent(String applicationName) implements SettingsAuditEvent {

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
        return SettingsOperation.ACTIVATE;
    }

    @Override
    public String entityName() {
        return applicationName;
    }
}
