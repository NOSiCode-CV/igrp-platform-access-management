package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.stereotype.Component;

/**
 * Component responsible for converting between {@link Role} entities and {@link RoleDTO} data transfer objects.
 * <p>
 * Used by command and query handlers to translate between internal domain models and external representations.
 */
@Component
public class RoleMapper {

    /**
     * Converts a {@link Role} entity to a {@link RoleDTO}.
     *
     * @param role the role entity to convert; must not be {@code null}
     * @return the corresponding {@link RoleDTO} with values mapped from the entity
     */
    public RoleDTO mapToDto(Role role) {
        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(role.getId());
        if (role.getParent() != null) {
            roleDTO.setParentId(role.getParent().getId());
        }
        roleDTO.setName(role.getName());
        roleDTO.setDescription(role.getDescription());
        roleDTO.setDepartmentId(role.getDepartment().getId());
        roleDTO.setStatus(role.getStatus());
        return roleDTO;
    }

    /**
     * Converts a {@link RoleDTO} along with its related {@link Department} and optional parent {@link Role}
     * into a new {@link Role} entity.
     *
     * @param request     the data transfer object containing new role information
     * @param department  the department to which the role belongs
     * @param parentRole  the parent role, if applicable (nullable)
     * @return the new {@link Role} entity
     */
    public Role mapToEntity(RoleDTO request, Department department, Role parentRole) {
        Role newRole = new Role();
        newRole.setName(request.getName());
        newRole.setDescription(request.getDescription());
        newRole.setStatus(request.getStatus());
        newRole.setDepartment(department);
        if (parentRole != null) {
            newRole.setParent(parentRole);
        }
        return newRole;
    }
}
