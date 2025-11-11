package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetRolesQuery implements Query {

  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;
  @NotBlank(message = "The field <code> is required")
  private String code;

}