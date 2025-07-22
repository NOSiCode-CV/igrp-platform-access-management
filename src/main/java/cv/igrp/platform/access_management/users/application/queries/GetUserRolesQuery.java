package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserRolesQuery implements Query {

  @NotNull(message = "The field <applicationId> is required.")
  private Integer applicationId;
  @NotBlank(message = "The field <username> is required.")
  private String username;

}