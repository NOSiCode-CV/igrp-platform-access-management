package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveResourceFromApplicationCommand implements Command {

  @NotBlank(message = "The field <name> is required")
  private String name;
  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;

}