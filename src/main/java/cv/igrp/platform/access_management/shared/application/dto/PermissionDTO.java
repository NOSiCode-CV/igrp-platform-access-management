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
public class PermissionDTO {

  
  
  private Integer id;
  @NotBlank(message = "The field <name> is required.")
	@Size(max = 60, message = "The field length <name> cannot be more than 60 characters.")
  
  private String name;
  @Size(max = 255, message = "The field length <description> cannot be more than 255 characters.")
  
  private String description;
  
  
  private Status status;
  @NotNull(message = "The field <applicationId> is required.")
  
  private Integer applicationId;

}