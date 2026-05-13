package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserDepartmentRolesQuery implements Query {

  @NotBlank(message = "The field <roleCode> is required")
  private String roleCode;
  @NotNull(message = "The field <id> is required")
  private String id;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;

}