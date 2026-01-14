/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class ResourceDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <name> is required")
	@Size(max = 255, message = "The field length <name> cannot be more than 255 characters")
  
  private String name ;
  
  
  private String description ;
  @NotNull(message = "The field <type> is required")
  
  private ResourceType type ;
  
  
  private Status status ;
  
  
  private List<String> applications = new ArrayList<>();
  
  @Valid
  private List<ResourceItemDTO> items = new ArrayList<>();
  @Size(max = 255, message = "The field length <externalId> cannot be more than 255 characters")
  
  private String externalId ;
  
  
  private String createdBy ;
  
  
  private String createdDate ;
  
  
  private String lastModifiedBy ;
  
  
  private String lastModifiedDate ;
  
  
  private List<String> permissions = new ArrayList<>();

}