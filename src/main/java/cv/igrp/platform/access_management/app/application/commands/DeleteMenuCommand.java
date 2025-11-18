package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMenuCommand implements Command {

  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;
  @NotBlank(message = "The field <menuCode> is required")
  private String menuCode;

}