package cv.igrp.platform.access_management.app.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.net.URI;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class ApplicationDTO {

  
  private Integer id;
  @NotBlank(message = "The field <code> is required.")
  private String code;
  @NotBlank(message = "The field <name> is required.")
	@Size(min = 5, message = "The field length <name> must be at least 5 characters.")
	@Size(max = 50, message = "The field length <name> cannot be more than 50 characters.")
  private String name;
  @Size(min = 5, message = "The field length <description> must be at least 5 characters.")
	@Size(max = 255, message = "The field length <description> cannot be more than 255 characters.")
  private String description;
  
  private Status status;
  @NotNull(message = "The field <type> is required.")
  private AppType type;
  @Size(max = 255, message = "The field length <owner> cannot be more than 255 characters.")
  private String owner;
  @Size(max = 255, message = "The field length <picture> cannot be more than 255 characters.")
  private String picture;
  
  private URI url;
  @Size(min = 3, message = "The field length <slug> must be at least 3 characters.")
	@Size(max = 50, message = "The field length <slug> cannot be more than 50 characters.")
  private String slug;
  
  private String createdBy;
  
  private String createdDate;
  
  private String lastModifiedBy;
  
  private String lastModifiedDate;

}