/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

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
public class DepartmentDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <code> is required")
	@Pattern(message = "Invalid value format for field <code>.", regexp = "^[A-Za-z0-9_-]+$")
  
  private String code ;
  @NotBlank(message = "The field <name> is required")
  
  private String name ;
  
  
  private String description ;
  
  
  private DepartmentStatus status ;
  
  
  private String icon ;
  
  
  private String parent_code ;

}