package cv.igrp.platform.access_management.app.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationCommand implements Command {

  private ApplicationDTO applicationDTO;

  @NotNull(message = "The field <id> is required.")
  private Integer id;

}