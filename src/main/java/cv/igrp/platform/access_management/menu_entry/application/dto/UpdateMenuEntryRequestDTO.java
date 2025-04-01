package cv.igrp.platform.access_management.menu_entry.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.Collection;

@IgrpDTO
public record UpdateMenuEntryRequestDTO (
  
  String name, 
  
  String icon, 
  
  String target, 
  
  String url, 
  
  Integer folderReferenceId, 
  
  Integer resourceItemId, 
  
  Collection<String> userPermissions
){}