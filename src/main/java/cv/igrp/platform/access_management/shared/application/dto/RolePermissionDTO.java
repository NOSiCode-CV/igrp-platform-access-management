package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record RolePermissionDTO (
  @NotNull(message = "The field <permission_id> is required.")
  Integer permission_id, 
  @NotNull(message = "The field <role_id> is required.")
  Integer role_id
){}