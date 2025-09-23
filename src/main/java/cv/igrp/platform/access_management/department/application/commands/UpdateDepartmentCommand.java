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
public class UpdateDepartmentCommand implements Command {

  
  private DepartmentDTO departmentdto;
  @NotBlank(message = "The field <code> is required")
  private String code;

}