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
public class RemovePermissionsFromDepartmentCommand implements Command {

  
  private List<String> removePermissionsFromDepartmentRequest;
  @NotBlank(message = "The field <code> is required")
  private String code;

}