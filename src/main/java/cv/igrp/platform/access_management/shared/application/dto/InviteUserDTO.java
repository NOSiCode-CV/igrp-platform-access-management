/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class InviteUserDTO  {

  @NotBlank(message = "Identifier type is required (EMAIL, PHONE, NIC)")
  private String identifierType;
  
  @NotBlank(message = "Identifier value is required")
  private String identifierValue;
  
  
  private String departmentCode ;
  
  
  private List<String> roles = new ArrayList<>();

}