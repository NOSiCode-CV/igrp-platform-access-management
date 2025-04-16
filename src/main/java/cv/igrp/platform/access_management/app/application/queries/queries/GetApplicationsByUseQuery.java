package cv.igrp.platform.access_management.app.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
<<<<<<<< HEAD:src/main/java/cv/igrp/platform/access_management/app/application/queries/queries/GetDepartmentsQuery.java
public class GetDepartmentsQuery implements Query {

========
public class GetApplicationsByUseQuery implements Query {

  @NotBlank(message = "The field <uid> is required.")
  private String uid;
>>>>>>>> main:src/main/java/cv/igrp/platform/access_management/app/application/queries/queries/GetApplicationsByUseQuery.java

}