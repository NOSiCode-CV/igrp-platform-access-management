package cv.igrp.platform.access_management.resource.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResourceCommand implements Command {

  
  private ResourceDTO resourcedto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}