package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePermissionCommand implements Command {

  
  private PermissionDTO permissiondto;
  @NotBlank(message = "The field <name> is required.")
  private String name;

}