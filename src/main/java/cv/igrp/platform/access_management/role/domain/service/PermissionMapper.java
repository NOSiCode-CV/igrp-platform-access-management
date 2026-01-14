package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Component responsible for mapping between {@link PermissionEntity} entities and {@link PermissionDTO} data transfer objects.
 * <p>
 * This class is used to transform domain-level permission data into a transport-friendly DTO and vice versa,
 * maintaining separation between persistence and external representations.
 * </p>
 */
@Component
public class PermissionMapper {

    /**
     * Converts a {@link PermissionEntity} entity to a {@link PermissionDTO}.
     *
     * @param permission the domain permission entity to convert
     * @return a corresponding {@link PermissionDTO} with all relevant fields populated
     */
    public PermissionDTO mapToDTO(PermissionEntity permission) {
        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(permission.getId());
        permissionDTO.setName(permission.getName());
        permissionDTO.setDescription(permission.getDescription());
        permissionDTO.setStatus(permission.getStatus());
        if(permission.getDepartments() != null) {
            permissionDTO.setDepartments(permission.getDepartments().stream().map(DepartmentEntity::getCode).toList());
        }
        return permissionDTO;
    }

    /**
     * Converts a {@link PermissionDTO} and its associated {@link DepartmentEntity} into a {@link PermissionEntity} entity.
     * <p>
     * If the {@code status} field in the DTO is {@code null}, it defaults to {@link Status#ACTIVE}.
     * The description field, if present, is trimmed before being set.
     * </p>
     *
     * @param request     the DTO containing permission data
     * @param department the department to associate with the permission
     * @return a {@link PermissionEntity} entity ready to be persisted
     */
    public PermissionEntity mapDtoToEntity(PermissionDTO request, DepartmentEntity department) {
        PermissionEntity newPermission = new PermissionEntity();
        newPermission.setStatus(Objects.nonNull(request.getStatus()) ? request.getStatus() : Status.ACTIVE);
        newPermission.setName(request.getName());
        if (request.getDescription() != null) {
            newPermission.setDescription(request.getDescription().trim());
        }
        return newPermission;
    }
}
