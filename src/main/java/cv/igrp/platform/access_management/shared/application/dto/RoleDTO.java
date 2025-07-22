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
public class RoleDTO {

  
  
  private Integer id ;
  @NotBlank(message = "The field <name> is required.")
	@Size(max = 15, message = "The field length <name> cannot be more than 15 characters.")
  
  private String name ;
  @Size(max = 255, message = "The field length <description> cannot be more than 255 characters.")
  
  private String description ;
  @NotBlank(message = "The field <departmentCode> is required.")
  
  private String departmentCode ;
  
  
  private String parentName ;
  
  
  private Status status ;

}