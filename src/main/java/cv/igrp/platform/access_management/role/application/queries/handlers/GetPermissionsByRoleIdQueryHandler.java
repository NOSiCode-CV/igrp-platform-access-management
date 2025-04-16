package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
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

import java.util.List;

@Service
public class GetPermissionsByRoleIdQueryHandler implements QueryHandler<GetPermissionsByRoleIdQuery, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;

    public GetPermissionsByRoleIdQueryHandler(RoleRepository roleRepository) {

        this.roleRepository = roleRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<PermissionDTO>> handle(GetPermissionsByRoleIdQuery query) {
        Role foundRole = roleRepository.findById(query.getId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Permission By Role ID", "Role with id: " + query.getId() + " not found.")
                ));
        List<PermissionDTO> permissionList = foundRole.getPermissions()
                .stream()
                .filter(permission -> permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE))
                .map(permission -> {
                    PermissionDTO permissionDTO = new PermissionDTO();
                    permissionDTO.setId(permission.getId());
                    permissionDTO.setName(permission.getName());
                    permissionDTO.setDescription(permission.getDescription());
                    permissionDTO.setStatus(permission.getStatus());
                    return permissionDTO;
                })
                .toList();
        return new ResponseEntity<>(permissionList, HttpStatus.OK);
    }

}