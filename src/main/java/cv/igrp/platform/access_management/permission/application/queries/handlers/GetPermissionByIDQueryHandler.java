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
import cv.igrp.platform.access_management.permission.application.queries.queries.GetPermissionByIDQuery;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPermissionByIDQueryHandler implements QueryHandler<GetPermissionByIDQuery, ResponseEntity<PermissionDTO>> {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public GetPermissionByIDQueryHandler(PermissionRepository permissionRepository, PermissionMapper permissionMapper) {

        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<PermissionDTO> handle(GetPermissionByIDQuery query) {
        Permission foundPermission = permissionRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Permission By ID", "Permission with id: " + query.getId() + " not found.")
                ));
        PermissionDTO responseDto = permissionMapper.mapToDTO(foundPermission);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }
}