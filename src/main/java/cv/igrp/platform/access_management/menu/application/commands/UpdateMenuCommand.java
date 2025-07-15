package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuCommand implements Command {

  
  private MenuEntryDTO menuentrydto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}