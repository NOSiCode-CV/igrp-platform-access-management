package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletePermissionCommand implements Command {

  @NotBlank(message = "The field <name> is required")
  private String name;

}