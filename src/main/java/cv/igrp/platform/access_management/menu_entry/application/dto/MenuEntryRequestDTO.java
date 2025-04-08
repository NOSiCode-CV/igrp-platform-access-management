package cv.igrp.platform.access_management.menu_entry.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import java.util.Collection;

@IgrpDTO
public record MenuEntryRequestDTO (
  @NotNull(message = "The field <type> is required.")
  MenuEntryType type, 
  
  String name, 
  
  String target, 
  @NotBlank(message = "The field <userPermissions> is required.")
	@Size(min = 1, message = "The field length <userPermissions> must be at least 1 characters.")
  Collection<String> userPermissions, 
  
  String icon, 
  
  String url, 
  
  Integer folderReferenceId, 
  
  Integer resourceItemId
){}