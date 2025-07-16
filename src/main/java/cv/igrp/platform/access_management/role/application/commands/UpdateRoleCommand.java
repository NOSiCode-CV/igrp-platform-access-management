package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleCommand implements Command {

  
  private RoleDTO roledto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}