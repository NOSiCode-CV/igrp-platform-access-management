package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetRolesQuery implements Query {

  @NotBlank(message = "The field <roleCode> is required")
  private String roleCode;
  @NotBlank(message = "The field <code> is required")
  private String code;

}