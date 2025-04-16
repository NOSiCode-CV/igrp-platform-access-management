package cv.igrp.platform.access_management.app.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
<<<<<<<< HEAD:src/main/java/cv/igrp/platform/access_management/app/application/queries/queries/GetApplicationQuery.java
public class GetApplicationQuery implements Query {
========
public class GetApplicationsByUserQuery implements Query {
>>>>>>>> main:src/main/java/cv/igrp/platform/access_management/app/application/queries/queries/GetApplicationsByUserQuery.java

  @NotBlank(message = "The field <uid> is required.")
  private String uid;

}