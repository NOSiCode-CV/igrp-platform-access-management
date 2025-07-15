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

  @NotNull(message = "The field <applicationId> is required.")
  private Integer applicationId;
  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <type> is required.")
  private String type;
  @NotBlank(message = "The field <status> is required.")
  private String status;
  @NotBlank(message = "The field <applicationCode> is required.")
  private String applicationCode;

}