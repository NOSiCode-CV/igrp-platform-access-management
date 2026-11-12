package cv.igrp.platform.access_management.permission_fetch.application.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/users/me/permissions?applicationCode=} response (spec
 * "Permission fetch contract").
 *
 * @param keys        the permission keys of the caller's active role in the application (v3-06)
 * @param scopes      each key's data scope, or the sorted comma list of several (v3-13, #21)
 * @param roles       the caller's role codes, compared by the {@code by_role} scope: the active
 *                    role; for a service account, every role it holds (v3-31, #39)
 * @param departments the codes of the caller's visible department subtree, compared by the
 *                    {@code department} scope: the active role's department and every department
 *                    under it; for a service account, under each of its roles' (v3-31, #39)
 */
public record PermissionSetDTO(List<String> keys, Map<String, String> scopes, List<String> roles,
                               List<String> departments) {
}
