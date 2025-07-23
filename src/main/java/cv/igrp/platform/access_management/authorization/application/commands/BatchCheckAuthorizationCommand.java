package cv.igrp.platform.access_management.authorization.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckRequestDTO;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchCheckAuthorizationCommand implements Command {

  
  private List<PermissionCheckRequestDTO> permissioncheckrequest;

}