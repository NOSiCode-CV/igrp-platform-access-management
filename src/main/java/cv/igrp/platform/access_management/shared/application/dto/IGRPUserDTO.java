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

  
  
  private String id ;
  
  
  private String name ;
  
  
  private String username ;
  @NotBlank(message = "The field <email> is required")
	@Email(message = "Invalid email format for field <email>")
  
  private String email ;
  
  
  private Status status ;
  
  
  private String picture ;
  
  
  private String signature ;

  @Size(max = 13, message = "The field <nic> must be at most 13 characters")
  private String nic;

  @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "The field <phoneNumber> must follow E.164 format (e.g., +1234567890)")
  private String phoneNumber;

}