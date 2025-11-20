package cv.igrp.platform.access_management.files.application.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetPrivateFileUrlQuery implements Query {

  @NotBlank(message = "The field <privateFilePath> is required")
  private String privateFilePath;

}