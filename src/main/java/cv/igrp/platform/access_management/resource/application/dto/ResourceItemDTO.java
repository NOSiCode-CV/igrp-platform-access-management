package cv.igrp.platform.access_management.resource.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class ResourceItemDTO {

  
  private Integer id;
  @NotBlank(message = "The field <name> is required.")
	@Size(min = 3, message = "The field length <name> must be at least 3 characters.")
	@Size(max = 100, message = "The field length <name> cannot be more than 100 characters.")
  private String name;
  @Size(min = 5, message = "The field length <url> must be at least 5 characters.")
	@Size(max = 255, message = "The field length <url> cannot be more than 255 characters.")
  private String url;
  
  private Integer permissionId;
  
  private Integer resourceId;

}