package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetCurrentUserPermissionsQuery implements Query {

  @NotBlank(message = "The field <roleCode> is required")
  private String roleCode;

}