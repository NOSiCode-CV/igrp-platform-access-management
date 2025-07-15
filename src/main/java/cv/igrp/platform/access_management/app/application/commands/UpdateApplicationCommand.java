package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationCommand implements Command {

  
  private ApplicationDTO applicationdto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}