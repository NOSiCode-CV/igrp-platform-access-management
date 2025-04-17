package cv.igrp.platform.access_management.users.application.commands.commands;

import java.util.List;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveRolesFromUserCommand implements Command {

  
  private RoleDTO roleDTO;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

  @NotNull(message = "The field <roleIds> is required.")
  private List<Integer> roleIds;

}