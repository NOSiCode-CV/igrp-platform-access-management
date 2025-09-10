/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

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
public class PermissionDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <name> is required")
	@Size(max = 60, message = "The field length <name> cannot be more than 60 characters")
	@Pattern(message = "Invalid value format for field <name>.", regexp = "^[A-Za-z0-9_-]+$")
  
  private String name ;
  @Size(max = 255, message = "The field length <description> cannot be more than 255 characters")
  
  private String description ;
  
  
  private Status status ;
  @NotBlank(message = "The field <departmentCode> is required")
  
  private String departmentCode ;

}