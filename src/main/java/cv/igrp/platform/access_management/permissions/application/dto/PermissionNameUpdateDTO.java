package cv.igrp.platform.access_management.permissions.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/permissions/{key}}: a permission's new display name and description, in any language. */
public record PermissionNameUpdateDTO(
        @NotBlank(message = "The field <name> is required")
        @Size(max = 255, message = "The field length <name> cannot be more than 255 characters")
        String name,
        @Size(max = 255, message = "The field length <description> cannot be more than 255 characters")
        String description) {
}
