package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetApplicationsByUserQuery implements Query {

  @NotBlank(message = "The field <uid> is required")
  private String uid;

}