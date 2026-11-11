package cv.igrp.platform.access_management.routes.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO;

/** A service's route registration, as received on {@code POST /api/routes/register}. */
public record RegisterRoutesCommand(RouteRegistrationRequestDTO registration) implements Command {
}
