package cv.igrp.platform.access_management.m2m.application.commands;

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
public class GetRolesForBusinessCommand implements Command {

  
  private List<String> getRolesForBusinessRequest;
  @NotNull(message = "The field <activeOnly> is required")
  private boolean activeOnly;
  @NotBlank(message = "The field <parentCode> is required")
  private String parentCode;
  @NotNull(message = "The field <includeChildrenRoles> is required")
  private boolean includeChildrenRoles;

}