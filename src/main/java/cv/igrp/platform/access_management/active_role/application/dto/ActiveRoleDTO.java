package cv.igrp.platform.access_management.active_role.application.dto;

import java.util.List;

/**
 * {@code GET|PUT /api/users/me/role/active?applicationCode=} response (v3-05).
 *
 * @param activeRole     the role the user acts under in the application, or null when none is chosen
 *                       and several are valid (the frontend then asks)
 * @param source         where {@code activeRole} comes from
 * @param availableRoles every role valid for the user in the application, for the picker
 */
public record ActiveRoleDTO(String applicationCode, RoleRefDTO activeRole, Source source,
                            List<RoleRefDTO> availableRoles) {

    public enum Source {
        /** The user's stored choice. */
        STORED,
        /** No choice stored, and it is the only valid role. */
        DEFAULT,
        /** Root: the super-admin role, in every application. */
        SUPER_ADMIN,
        /** No choice stored and several (or no) valid roles: no active role. */
        NONE
    }

    /** A role as the picker shows it; role codes are unique only within a department. */
    public record RoleRefDTO(String code, String name, String departmentCode) {
    }
}
