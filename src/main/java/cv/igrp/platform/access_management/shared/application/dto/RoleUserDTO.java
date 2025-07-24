/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record RoleUserDTO (
  @NotBlank(message = "The field <role_name> is required.")
	@Pattern(message = "Invalid value format for field <role_name>.", regexp = "^[A-Za-z0-9_-]+$")
  String role_name
){}