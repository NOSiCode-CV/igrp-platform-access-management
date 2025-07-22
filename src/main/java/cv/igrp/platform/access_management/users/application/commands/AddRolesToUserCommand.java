package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRolesToUserCommand implements Command {

  
  private RoleUserDTO roleuserdto;
  @NotBlank(message = "The field <username> is required.")
  private String username;

}