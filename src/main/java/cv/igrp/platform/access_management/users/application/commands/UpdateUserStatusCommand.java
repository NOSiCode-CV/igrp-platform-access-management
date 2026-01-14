package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusCommand implements Command {

  @NotBlank(message = "The field <value> is required")
  private String value;
  @NotNull(message = "The field <id> is required")
  private Integer id;

}