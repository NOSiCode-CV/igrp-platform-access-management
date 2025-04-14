package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import java.util.Collection;

@IgrpDTO
public record DepartmentDTO (
  @NotNull(message = "The field <id> is required.")
  Integer id, 
  @NotBlank(message = "The field <code> is required.")
  String code, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  
  Collection<String> description, 
  @NotNull(message = "The field <status> is required.")
  DepartmentStatus status, 
  @NotNull(message = "The field <application_id> is required.")
  Integer application_id, 
  
  Integer parent_id
){}