package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDepartmentPermissionsQuery implements Query {

  @NotBlank(message = "The field <permissionName> is required")
  private String permissionName;
  /** Optional filter — restricts to permissions attached to the resource with this name. */
  private String resourceName;
  @NotBlank(message = "The field <code> is required")
  private String code;

}