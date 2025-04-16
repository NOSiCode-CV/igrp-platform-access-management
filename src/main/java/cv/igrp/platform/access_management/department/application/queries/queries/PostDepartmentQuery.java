package cv.igrp.platform.access_management.department.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDepartmentQuery implements Query {

  
  private DepartmentDTO departmentdto;

}