package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.constraints.*;


@IgrpDTO
public record RoleUserDTO (
  @NotNull(message = "The field <user_id> is required.")
  Integer user_id, 
  @NotNull(message = "The field <role_id> is required.")
  Integer role_id
){}