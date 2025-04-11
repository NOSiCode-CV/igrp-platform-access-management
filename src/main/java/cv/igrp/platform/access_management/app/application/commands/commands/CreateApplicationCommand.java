package cv.igrp.platform.access_management.app.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApplicationCommand implements Command {

  
  private ApplicationDTO applicationdto;

}