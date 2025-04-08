package cv.igrp.platform.access_management.app.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import java.util.Collection;

@IgrpDTO
public record AppRequestDTO (
  @NotNull(message = "The field <type> is required.")
  AppType type, 
  @NotBlank(message = "The field <owner> is required.")
  String owner, 
  @NotBlank(message = "The field <code> is required.")
  String code, 
  
  String description, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  
  String url, 
  
  String slug, 
  @NotBlank(message = "The field <userPermissions> is required.")
	@Size(min = 1, message = "The field length <userPermissions> must be at least 1 characters.")
  Collection<String> userPermissions
){}