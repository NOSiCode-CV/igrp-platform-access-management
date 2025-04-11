package cv.igrp.platform.access_management.menu_entry.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMenuCommand implements Command {

  @NotNull(message = "The field <id> is required.")
  private Integer id;

}