package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDepartmentCommand implements Command {

  
  private DepartmentDTO departmentdto;

}