package cv.igrp.platform.access_management.users.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUsersQuery implements Query {

  @NotNull(message = "The field <applicationId> is required.")
  private Integer applicationid;
  @NotNull(message = "The field <departmentId> is required.")
  private Integer departmentid;
  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <username> is required.")
  private String username;
  @NotBlank(message = "The field <email> is required.")
  private String email;

}