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
public class GetUsersCommand implements Command {

  
  private List<Integer> getUsersRequest;
  @NotNull(message = "The field <applicationCode> is required.")
  private Integer applicationCode;
  @NotNull(message = "The field <departmentCode> is required.")
  private Integer departmentCode;
  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <username> is required.")
  private String username;
  @NotBlank(message = "The field <email> is required.")
  private String email;

}