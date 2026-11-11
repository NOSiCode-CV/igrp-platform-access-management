package cv.igrp.platform.access_management.active_role.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.active_role.application.dto.SwitchActiveRoleRequestDTO;

/** The caller switches their active role in one application. */
public record SwitchActiveRoleCommand(String applicationCode, SwitchActiveRoleRequestDTO request) implements Command {
}
