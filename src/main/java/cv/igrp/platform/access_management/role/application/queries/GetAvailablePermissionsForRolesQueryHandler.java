package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@Component
public class GetAvailablePermissionsForRolesQueryHandler implements QueryHandler<GetAvailablePermissionsForRolesQuery, ResponseEntity<List<PermissionDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailablePermissionsForRolesQueryHandler.class);

    private final PermissionEntityRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public GetAvailablePermissionsForRolesQueryHandler(PermissionEntityRepository permissionRepository,
                                                       PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<PermissionDTO>> handle(GetAvailablePermissionsForRolesQuery query) {
        LOGGER.info("Fetching all available permissions for role: {}", query.getName());

        List<PermissionDTO> permissions = permissionRepository.findAvailablePermissionsForRole(query.getName())
                .stream()
                .map(permissionMapper::mapToDTO)
                .toList();

        LOGGER.info("Found {} available permissions for role {}", permissions.size(), query.getName());
        return ResponseEntity.ok(permissions);
    }

}