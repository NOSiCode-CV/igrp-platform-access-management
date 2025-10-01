package cv.igrp.platform.access_management.resource.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetResourceByIdQuery implements Query {

  @NotBlank(message = "The field <name> is required")
  private String name;

}