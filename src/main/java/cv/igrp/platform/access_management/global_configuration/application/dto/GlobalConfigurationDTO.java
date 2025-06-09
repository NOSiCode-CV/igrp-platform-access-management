package cv.igrp.platform.access_management.global_configuration.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class GlobalConfigurationDTO {

  @NotBlank(message = "The field <config> is required.")
  
  private String config;
  @NotNull(message = "The field <type> is required.")
  
  private GlobalConfigurationType type;

}