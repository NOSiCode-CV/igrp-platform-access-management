package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

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
