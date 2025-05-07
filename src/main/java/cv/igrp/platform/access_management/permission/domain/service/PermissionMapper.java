package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PermissionMapper {

    public PermissionDTO mapToDTO(Permission permission) {
        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(permission.getId());
        permissionDTO.setName(permission.getName());
        permissionDTO.setDescription(permission.getDescription());
        permissionDTO.setStatus(permission.getStatus());
        permissionDTO.setApplicationId(permission.getApplication().getId());
        return permissionDTO;
    }

    public Permission mapDtoToEntity(PermissionDTO request, Application application) {
        Permission newPermission = new Permission();
        newPermission.setStatus(Objects.nonNull(request.getStatus()) ? request.getStatus() : Status.ACTIVE);
        newPermission.setName(request.getName());
        if (request.getDescription() != null) {
            newPermission.setDescription(request.getDescription().trim());
        }
        newPermission.setApplication(application);
        return newPermission;
    }
}
