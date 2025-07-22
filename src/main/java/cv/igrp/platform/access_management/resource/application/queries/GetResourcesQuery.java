package cv.igrp.platform.access_management.resource.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetResourcesQuery implements Query {

  @NotBlank(message = "The field <name> is required.")
  private String name;
  @NotBlank(message = "The field <type> is required.")
  private String type;
  @NotBlank(message = "The field <externalID> is required.")
  private String externalID;
  @NotBlank(message = "The field <applicationCode> is required.")
  private String applicationCode;
  @NotBlank(message = "The field <description> is required.")
  private String description;

}