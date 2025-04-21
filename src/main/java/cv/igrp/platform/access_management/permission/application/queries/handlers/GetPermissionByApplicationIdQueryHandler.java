package cv.igrp.platform.access_management.permission.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.permission.application.queries.queries.GetPermissionByApplicationIdQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GetPermissionByApplicationIdQueryHandler implements QueryHandler<GetPermissionByApplicationIdQuery, ResponseEntity<List<PermissionDTO>>> {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public GetPermissionByApplicationIdQueryHandler(PermissionRepository permissionRepository, PermissionMapper permissionMapper) {

        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<PermissionDTO>> handle(GetPermissionByApplicationIdQuery query) {
        Set<Permission> permissionList = permissionRepository.findAll()
                .stream()
                .filter(permission -> permission.getApplication().getId().equals(query.getApplicationId())
                        && (permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE)))
                .collect(Collectors.toSet());

        List<PermissionDTO> permissionDTO = permissionList.stream()
                .map(permissionMapper::mapToDTO)
                .toList();
        return new ResponseEntity<>(permissionDTO, HttpStatus.OK);
    }

}