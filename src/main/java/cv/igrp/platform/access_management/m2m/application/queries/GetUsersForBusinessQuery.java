package cv.igrp.platform.access_management.m2m.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUsersForBusinessQuery implements Query {

  @NotNull(message = "The field <activeOnly> is required")
  private boolean activeOnly;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;
  @NotBlank(message = "The field <roleCode> is required")
  private String roleCode;
  @NotBlank(message = "The field <permissionName> is required")
  private String permissionName;
  @NotNull(message = "The field <includeChildrenDepartments> is required")
  private boolean includeChildrenDepartments;
  @NotNull(message = "The field <includeChildrenRoles> is required")
  private boolean includeChildrenRoles;

}