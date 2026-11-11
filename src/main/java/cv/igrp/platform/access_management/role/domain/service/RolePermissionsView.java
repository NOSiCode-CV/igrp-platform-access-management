package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.PermissionScope;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** A role's permissions as the API returns them, each with its scope in the role (#21). */
@Component
public class RolePermissionsView {

    private final PermissionMapper mapper;
    private final RolePermissionScopes scopes;

    public RolePermissionsView(PermissionMapper mapper, RolePermissionScopes scopes) {
        this.mapper = mapper;
        this.scopes = scopes;
    }

    public List<PermissionDTO> of(RoleEntity role) {
        Map<Integer, PermissionScope> byId = scopes.byPermissionId(role.getId());
        return role.getPermissions().stream()
                .filter(permission -> permission.getStatus() != Status.DELETED)
                .map(permission -> {
                    PermissionDTO dto = mapper.mapToDTO(permission);
                    PermissionScope scope = byId.get(permission.getId());
                    dto.setScope(scope != null ? scope.code() : null);
                    return dto;
                })
                .toList();
    }
}
