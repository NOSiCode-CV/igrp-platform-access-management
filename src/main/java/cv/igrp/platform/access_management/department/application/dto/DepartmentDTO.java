package cv.igrp.platform.access_management.department.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.Status;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class DepartmentDTO {

  
  private Integer id;
  
  private String code;
  
  private String name;
  
  private String description;
  
  private Status status;
  
  private Integer applicationId;
  
  private Integer parentId;

}