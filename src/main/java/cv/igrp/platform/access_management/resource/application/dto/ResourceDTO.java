package cv.igrp.platform.access_management.resource.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class ResourceDTO {

  
  private Integer id;
  @NotBlank(message = "The field <name> is required.")
	@Size(min = 5, message = "The field length <name> must be at least 5 characters.")
	@Size(max = 100, message = "The field length <name> cannot be more than 100 characters.")
  private String name;
  @NotNull(message = "The field <type> is required.")
  private ResourceType type;
  
  private Status status;
  @NotNull(message = "The field <applicationId> is required.")
  private Integer applicationId;
  
  private List<ResourceItemDTO> items;
  @Size(min = 3, message = "The field length <externalId> must be at least 3 characters.")
	@Size(max = 255, message = "The field length <externalId> cannot be more than 255 characters.")
  private String externalId;

}