package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuCommand implements Command {

  
  private MenuEntryDTO menuentrydto;
  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;
  @NotBlank(message = "The field <menuCode> is required")
  private String menuCode;

}