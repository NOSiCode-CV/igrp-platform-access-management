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
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class RoleDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <code> is required")
	@Size(max = 255, message = "The field length <code> cannot be more than 255 characters")
	@Pattern(message = "Invalid value format for field <code>.", regexp = "^[A-Za-z0-9_.-]+$")
  
  private String code ;
  @NotBlank(message = "The field <name> is required")
  
  private String name ;
  @Size(max = 255, message = "The field length <description> cannot be more than 255 characters")
  
  private String description ;
  
  
  private String departmentCode ;
  
  
  private String parentCode ;
  
  
  private Status status ;
  
  
  private String icon ;
  
  
  private List<String> permissions = new ArrayList<>();

}