package cv.igrp.platform.access_management.global_configuration.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetGlobalConfigurationCommand implements Command {

  
  private GlobalConfigurationDTO globalconfiguration;

}