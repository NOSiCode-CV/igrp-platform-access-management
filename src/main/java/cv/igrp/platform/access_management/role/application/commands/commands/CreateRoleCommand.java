package cv.igrp.platform.access_management.role.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.role.application.dto.RoleDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleCommand implements Command {

  
  private RoleDTO roledto;

}