package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUsersQuery implements Query {

  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;
  @NotBlank(message = "The field <name> is required")
  private String name;
  @NotNull(message = "The field <id> is required")
  private String id;
  @NotBlank(message = "The field <email> is required")
  private String email;

}