package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetPermissionByApplicationIdQuery implements Query {

  @NotNull(message = "The field <resourceId> is required")
  private Integer resourceId;
  @NotBlank(message = "The field <resourceName> is required")
  private String resourceName;

}