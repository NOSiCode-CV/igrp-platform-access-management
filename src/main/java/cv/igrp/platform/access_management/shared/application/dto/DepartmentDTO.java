package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class DepartmentDTO {

  
  
  private Integer id;
  @NotBlank(message = "The field <code> is required.")
  
  private String code;
  @NotBlank(message = "The field <name> is required.")
  
  private String name;
  
  
  private String description;
  
  
  private DepartmentStatus status;
  @NotNull(message = "The field <application_id> is required.")
  
  private Integer application_id;
  
  
  private Integer parent_id;

}