package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMenusQuery implements Query {

  @NotBlank(message = "The field <name> is required")
  private String name;
  @NotBlank(message = "The field <type> is required")
  private String type;
  @NotBlank(message = "The field <status> is required")
  private String status;
  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;
  @NotBlank(message = "The field <code> is required")
  private String code;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;

}