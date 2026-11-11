package cv.igrp.platform.access_management.active_role.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code PUT /api/users/me/role/active?applicationCode=} body (v3-05).
 * {@code departmentCode} is needed only when the role code exists in more
 * than one of the application's departments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchActiveRoleRequestDTO {

    @NotBlank(message = "The field <roleCode> is required")
    @Size(max = 255, message = "The field length <roleCode> cannot be more than 255 characters")
    private String roleCode;

    @Size(max = 255, message = "The field length <departmentCode> cannot be more than 255 characters")
    private String departmentCode;
}
