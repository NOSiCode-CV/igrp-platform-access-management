package cv.igrp.platform.access_management.resource.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import cv.igrp.platform.access_management.resource.application.constants.ResourceType;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemRequestDTO;
import java.util.Collection;

@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class ResourceRequestDTO {

  @NotNull(message = "The field <type> is required.")
  private ResourceType type;
  @NotBlank(message = "The field <url> is required.")
  private String url;
  
  private String description;
  @NotNull(message = "The field <resourceItems> is required.")
  private Collection<ResourceItemRequestDTO> resourceItems;
  
  private String identityConfig;

}