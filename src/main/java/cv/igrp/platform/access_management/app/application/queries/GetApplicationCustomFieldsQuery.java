package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetApplicationCustomFieldsQuery implements Query {

  @NotBlank(message = "The field <code> is required.")
  private String code;

}