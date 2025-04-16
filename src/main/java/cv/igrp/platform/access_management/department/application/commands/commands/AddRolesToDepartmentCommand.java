package cv.igrp.platform.access_management.department.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.shared.application.dto.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRolesToDepartmentCommand implements Command {
    @NotNull private Integer departmentId;
    private List<RoleDTO> roles;
}