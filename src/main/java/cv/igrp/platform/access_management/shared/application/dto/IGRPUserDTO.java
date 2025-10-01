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
public class IGRPUserDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <name> is required")
  
  private String name ;
  @NotBlank(message = "The field <username> is required")
	@Pattern(message = "Invalid value format for field <username>.", regexp = "^[A-Za-z0-9_-]+$")
  
  private String username ;
  @NotBlank(message = "The field <email> is required")
	@Email(message = "Invalid email format for field <email>")
  
  private String email ;
  
  
  private Status status ;
  
  
  private String picture ;
  
  
  private String signature ;

}