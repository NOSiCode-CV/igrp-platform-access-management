package cv.igrp.platform.access_management.app.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetApplicationsQuery implements Query {

  @NotBlank(message = "The field <code> is required.")
  private String code;
  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <slug> is required.")
  private String slug;

}