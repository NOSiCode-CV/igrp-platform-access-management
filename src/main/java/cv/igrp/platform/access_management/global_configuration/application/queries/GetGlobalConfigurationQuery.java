package cv.igrp.platform.access_management.global_configuration.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetGlobalConfigurationQuery implements Query {

  @NotBlank(message = "The field <type> is required.")
  private String type;

}