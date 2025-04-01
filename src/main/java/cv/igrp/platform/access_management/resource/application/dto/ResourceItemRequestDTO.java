package cv.igrp.platform.access_management.resource.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;

import cv.igrp.platform.access_management.resource.application.constants.ResourceItemType;

@IgrpDTO
public record ResourceItemRequestDTO (
  @NotBlank(message = "The field <name> is required.")
  String name, 
  @NotBlank(message = "The field <url> is required.")
  String url, 
  @NotNull(message = "The field <resourceItemType> is required.")
  ResourceItemType resourceItemType, 
  
  String description
){}