package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record ResourceDTO (
  @NotNull(message = "The field <id> is required.")
  Integer id, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  @NotBlank(message = "The field <type> is required.")
  String type, 
  @NotBlank(message = "The field <status> is required.")
  String status, 
  @NotNull(message = "The field <application_id> is required.")
  Integer application_id
){}