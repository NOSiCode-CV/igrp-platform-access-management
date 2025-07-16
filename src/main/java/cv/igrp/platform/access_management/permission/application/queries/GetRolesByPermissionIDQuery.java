package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetRolesByPermissionIDQuery implements Query {

  @NotNull(message = "The field <id> is required.")
  private Integer id;

}