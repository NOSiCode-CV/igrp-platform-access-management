package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetRoleChildrenQuery implements Query {

  @NotNull(message = "The field <level> is required")
  private Integer level;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;
  @NotBlank(message = "The field <roleCode> is required")
  private String roleCode;

}