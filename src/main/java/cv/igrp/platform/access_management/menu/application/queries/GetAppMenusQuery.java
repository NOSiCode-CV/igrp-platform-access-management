package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAppMenusQuery implements Query {

  @NotBlank(message = "The field <appCode> is required")
  private String appCode;

}