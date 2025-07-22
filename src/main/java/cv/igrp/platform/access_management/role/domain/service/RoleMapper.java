package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.springframework.stereotype.Component;

/**
 * Component responsible for converting between {@link RoleEntity} entities and {@link RoleDTO} data transfer objects.
 * <p>
 * Used by command and query handlers to translate between internal domain models and external representations.
 */
@Component
public class RoleMapper {

    /**
     * Converts a {@link RoleEntity} entity to a {@link RoleDTO}.
     *
     * @param role the role entity to convert; must not be {@code null}
     * @return the corresponding {@link RoleDTO} with values mapped from the entity
     */
    public RoleDTO mapToDto(RoleEntity role) {
        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setName(role.getName());
        if (role.getParent() != null) {
            roleDTO.setParentName(role.getParent().getName());
        }
        roleDTO.setName(role.getName());
        roleDTO.setDescription(role.getDescription());
        roleDTO.setDepartmentCode(role.getDepartment().getCode());
        roleDTO.setStatus(role.getStatus());
        return roleDTO;
    }

    /**
     * Converts a {@link RoleDTO} along with its related {@link DepartmentEntity} and optional parent {@link RoleEntity}
     * into a new {@link RoleEntity} entity.
     *
     * @param request     the data transfer object containing new role information
     * @param department  the department to which the role belongs
     * @param parentRole  the parent role, if applicable (nullable)
     * @return the new {@link RoleEntity} entity
     */
    public RoleEntity mapToEntity(RoleDTO request, DepartmentEntity department, RoleEntity parentRole) {
        RoleEntity newRole = new RoleEntity();
        newRole.setName(request.getName());
        newRole.setDescription(request.getDescription());
        newRole.setStatus(request.getStatus() != null ? request.getStatus() : Status.ACTIVE);
        newRole.setDepartment(department);
        if (parentRole != null) {
            newRole.setParent(parentRole);
        }
        return newRole;
    }
}
