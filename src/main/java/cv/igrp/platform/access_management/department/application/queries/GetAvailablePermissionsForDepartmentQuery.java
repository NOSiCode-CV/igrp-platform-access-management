package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAvailablePermissionsForDepartmentQuery implements Query {

  @NotBlank(message = "The field <resourceName> is required")
  private String resourceName;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;

}