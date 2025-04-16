package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;


@IgrpDTO
public record ResourceItemDTO (
  @NotNull(message = "The field <id> is required.")
  Integer id, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  
  String url, 
  @NotNull(message = "The field <permission_id> is required.")
  Integer permission_id, 
  @NotNull(message = "The field <resource_id> is required.")
  Integer resource_id
){}