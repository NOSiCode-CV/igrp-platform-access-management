package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveRolesFromUserCommand implements Command {

  
  private List<String> removeRolesFromUserRequest;
  @NotNull(message = "The field <id> is required")
  private Integer id;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;

}