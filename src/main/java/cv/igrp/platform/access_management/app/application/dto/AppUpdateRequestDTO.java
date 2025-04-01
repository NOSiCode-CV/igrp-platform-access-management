package cv.igrp.platform.access_management.app.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import cv.igrp.platform.access_management.app.application.constants.AppType;
import java.util.Collection;

@IgrpDTO
public record AppUpdateRequestDTO (
  
  AppType type, 
  
  String owner, 
  
  String description, 
  
  String name, 
  
  String url, 
  
  String slug, 
  @Size(min = 1, message = "The field length <userPermissions> must be at least 1 characters.")
  Collection<String> userPermissions
){}