package cv.igrp.platform.access_management.permission.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.application.queries.queries.GetPermissionByApplicationIdQuery;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Query handler responsible for retrieving all active or inactive {@link Permission} entities
 * associated with a specific {@link Application} ID.
 * <p>
 * This handler filters the result in-memory after fetching all permissions,
 * returning only those with the provided application ID and a {@link Status} of
 * {@code ACTIVE} or {@code INACTIVE}.
 * </p>
 *
 * @see GetPermissionByApplicationIdQuery
 * @see PermissionRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 */
@Slf4j
@Service
public class GetPermissionByApplicationIdQueryHandler implements QueryHandler<GetPermissionByApplicationIdQuery, ResponseEntity<List<PermissionDTO>>> {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Constructs a new instance of the handler with required dependencies.
     *
     * @param permissionRepository repository used to retrieve all permissions
     * @param permissionMapper     mapper to convert {@link Permission} entities to {@link PermissionDTO}
     */
    public GetPermissionByApplicationIdQueryHandler(PermissionRepository permissionRepository, PermissionMapper permissionMapper) {

        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }


    /**
     * Handles the query by filtering all permissions by application ID and status.
     *
     * @param query the query containing the application ID to filter by
     * @return a {@link ResponseEntity} containing the filtered list of {@link PermissionDTO}
     */
    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<PermissionDTO>> handle(GetPermissionByApplicationIdQuery query) {
        log.info("Get Permission with Application ID {}", query.getApplicationId());
        Set<Permission> permissionList = permissionRepository.findAll()
                .stream()
                .filter(permission -> resolveCondition(permission, query)
                        && (permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE)))
                .collect(Collectors.toSet());

        List<PermissionDTO> permissionDTO = permissionList.stream()
                .map(permissionMapper::mapToDTO)
                .toList();
        return new ResponseEntity<>(permissionDTO, HttpStatus.OK);
    }

    private boolean resolveCondition(Permission permission, GetPermissionByApplicationIdQuery query) {

        if(query.getApplicationId() != null && query.getApplicationId() > 0) {
            return permission.getApplication().getId().equals(query.getApplicationId());
        }

        if(query.getApplicationCode() != null && !query.getApplicationCode().isEmpty()) {
            return permission.getApplication().getCode().equals(query.getApplicationCode());
        }

        throw IgrpResponseStatusException.of(
                HttpStatus.BAD_REQUEST,
                        "No application filter provided",
                        "No application filter provided in the request. Must either be <applicationId> or <applicationCode>"
        );

    }

}