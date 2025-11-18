package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMenusToDepartmentCommand implements Command {

  
  private List<String> addMenusToDepartmentRequest;
  @NotBlank(message = "The field <departmentCode> is required")
  private String departmentCode;
  @NotBlank(message = "The field <applicationCode> is required")
  private String applicationCode;

}