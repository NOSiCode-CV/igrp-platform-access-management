package cv.igrp.platform.access_management.permission.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.application.queries.queries.GetRolesByPermissionIDQuery;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetRolesByPermissionIDQueryHandler implements QueryHandler<GetRolesByPermissionIDQuery, ResponseEntity<List<RoleDTO>>> {

    private final RoleMapper roleMapper;
    private final PermissionRepository permissionRepository;

    public GetRolesByPermissionIDQueryHandler(RoleMapper roleMapper, PermissionRepository permissionRepository) {

        this.roleMapper = roleMapper;
        this.permissionRepository = permissionRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetRolesByPermissionIDQuery query) {
        Permission permissionFound = permissionRepository.findById(query.getId())
                .filter(permission -> permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE))
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Role By Permission ID", "Permission with id: " + query.getId() + " not found.")
                ));
        List<RoleDTO> response = permissionFound.getRoles()
                .stream()
                .filter(role -> role.getStatus().equals(Status.ACTIVE) || role.getStatus().equals(Status.INACTIVE))
                .map(roleMapper::mapToDto)
                .toList();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}