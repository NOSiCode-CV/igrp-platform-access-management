package cv.igrp.platform.access_management.menu_entry.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;

import cv.igrp.platform.access_management.menu_entry.application.constants.MenuEntryType;

@IgrpDTO
public record FolderDTO (
  
  MenuEntryType type, 
  
  Integer id, 
  
  String name
){}