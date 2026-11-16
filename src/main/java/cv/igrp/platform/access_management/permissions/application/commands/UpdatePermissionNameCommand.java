package cv.igrp.platform.access_management.permissions.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionNameUpdateDTO;

/** Edit a permission's display name and description, by its immutable key. */
public record UpdatePermissionNameCommand(String key, PermissionNameUpdateDTO update) implements Command {
}
