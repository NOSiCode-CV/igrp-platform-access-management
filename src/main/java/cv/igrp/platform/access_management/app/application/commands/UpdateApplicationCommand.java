package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationCommand implements Command {

  
  private ApplicationDTO applicationdto;
  @NotBlank(message = "The field <code> is required")
  private String code;

}