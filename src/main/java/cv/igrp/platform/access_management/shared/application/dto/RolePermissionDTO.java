/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record RolePermissionDTO (
  @NotBlank(message = "The field <permission_name> is required.")
  String permission_name, 
  @NotBlank(message = "The field <role_name> is required.")
  String role_name
){}