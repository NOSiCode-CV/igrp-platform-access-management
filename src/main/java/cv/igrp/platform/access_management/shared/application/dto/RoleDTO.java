package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record RoleDTO (
  @NotNull(message = "The field <id> is required.")
  Integer id, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  
  String description, 
  @NotBlank(message = "The field <status> is required.")
  String status, 
  @NotBlank(message = "The field <department_id> is required.")
  String department_id, 
  
  String role_id
){}