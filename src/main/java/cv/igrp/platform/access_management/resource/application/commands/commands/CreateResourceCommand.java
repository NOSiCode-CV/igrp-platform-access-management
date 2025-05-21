package cv.igrp.platform.access_management.resource.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateResourceCommand implements Command {

  private ResourceDTO resourcedto;

}