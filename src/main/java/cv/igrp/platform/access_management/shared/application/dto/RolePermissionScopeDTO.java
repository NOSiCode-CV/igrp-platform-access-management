package cv.igrp.platform.access_management.shared.application.dto;

/**
 * One entry of {@code PUT /api/departments/{departmentCode}/roles/{roleCode}/permissions/scopes}
 * (#21): the scope a permission of the role should have. Checked by the handler,
 * which answers 400 for a missing key or an unknown scope.
 *
 * @param key   the permission's route key, which the role must already hold
 * @param scope {@code all}, {@code own}, {@code by_role} or {@code department}
 */
public record RolePermissionScopeDTO(String key, String scope) {
}
