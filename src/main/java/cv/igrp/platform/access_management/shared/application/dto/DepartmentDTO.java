package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import java.util.Collection;

@IgrpDTO
public record DepartmentDTO (
  
  String departmentName, 
  
  String code, 
  
  DepartmentStatus status, 
  
  Collection<String> availablePermissions, 
  
  String application
){}