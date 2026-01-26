package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetActiveCurrentUserRoleCommand implements Command {

  
  private RoleDepartmentDTO roledepartmentdto;

}