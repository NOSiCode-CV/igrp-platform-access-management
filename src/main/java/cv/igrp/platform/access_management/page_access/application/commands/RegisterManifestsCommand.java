package cv.igrp.platform.access_management.page_access.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO;

/** A frontend's page manifests, as received on {@code POST /api/manifests/register}. */
public record RegisterManifestsCommand(ManifestRegistrationRequestDTO registration) implements Command {
}
