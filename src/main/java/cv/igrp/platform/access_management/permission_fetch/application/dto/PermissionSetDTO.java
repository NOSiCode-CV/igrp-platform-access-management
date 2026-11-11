package cv.igrp.platform.access_management.permission_fetch.application.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/users/me/permissions?applicationCode=} response (v3-06,
 * spec "Permission fetch contract"): the permission keys of the caller's
 * active role in the application, and each key's data scope ({@code all}
 * until scopes on role permissions land, #21).
 */
public record PermissionSetDTO(List<String> keys, Map<String, String> scopes) {
}
