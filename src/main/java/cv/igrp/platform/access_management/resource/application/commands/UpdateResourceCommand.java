package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResourceCommand implements Command {

  
  private ResourceDTO resourcedto;
  @NotBlank(message = "The field <name> is required")
  private String name;

}