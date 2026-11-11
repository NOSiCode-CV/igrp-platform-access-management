package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.shared.application.dto.RolePermissionScopeDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Sets the scope of some of a role's permissions in one call (#21). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolePermissionScopesCommand implements Command {

    private List<RolePermissionScopeDTO> scopes;
    @NotBlank(message = "The field <departmentCode> is required")
    private String departmentCode;
    @NotBlank(message = "The field <roleCode> is required")
    private String roleCode;
}
