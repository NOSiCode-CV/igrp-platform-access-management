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
public class GetUsersCommand implements Command {

  
  private List<Integer> getUsersRequest;
  @NotNull(message = "The field <applicationId> is required.")
  private Integer applicationId;
  @NotNull(message = "The field <departmentId> is required.")
  private Integer departmentId;
  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <username> is required.")
  private String username;
  @NotBlank(message = "The field <email> is required.")
  private String email;

}