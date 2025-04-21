package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.role.application.queries.queries.GetPermissionsByRoleIdQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetPermissionsByRoleIdQueryHandler implements QueryHandler<GetPermissionsByRoleIdQuery, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    public GetPermissionsByRoleIdQueryHandler(RoleRepository roleRepository, PermissionMapper permissionMapper) {

        this.roleRepository = roleRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<PermissionDTO>> handle(GetPermissionsByRoleIdQuery query) {
        Role foundRole = roleRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Permission By Role ID", "Role with id: " + query.getId() + " not found.")
                ));
        List<PermissionDTO> permissionList = foundRole.getPermissions()
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .map(permissionMapper::mapToDTO)
                .toList();
        return new ResponseEntity<>(permissionList, HttpStatus.OK);
    }

}