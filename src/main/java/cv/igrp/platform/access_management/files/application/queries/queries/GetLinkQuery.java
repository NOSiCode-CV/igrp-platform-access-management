package cv.igrp.platform.access_management.files.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetLinkQuery implements Query {

  @NotBlank(message = "The field <id> is required.")
  private String id;

}