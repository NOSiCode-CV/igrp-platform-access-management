package cv.igrp.platform.access_management.users.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveRolesFromUserCommand implements Command {

  
  private List<Integer> removeRolesFromUserRequest;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}