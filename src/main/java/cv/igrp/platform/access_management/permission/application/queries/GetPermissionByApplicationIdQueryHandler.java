package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving all active or inactive {@link PermissionEntity} entities
 * associated with a specific {@link ApplicationEntity} ID.
 * <p>
 * This handler filters the result in-memory after fetching all permissions,
 * returning only those with the provided application ID and a {@link Status} of
 * {@code ACTIVE} or {@code INACTIVE}.
 * </p>
 *
 * @see GetPermissionByApplicationIdQuery
 * @see PermissionEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 */
@Slf4j
@Component
public class GetPermissionByApplicationIdQueryHandler implements QueryHandler<GetPermissionByApplicationIdQuery, ResponseEntity<List<PermissionDTO>>>{

  private final PermissionEntityRepository permissionRepository;
  private final PermissionMapper permissionMapper;

  /**
   * Constructs a new instance of the handler with required dependencies.
   *
   * @param permissionRepository repository used to retrieve all permissions
   * @param permissionMapper     mapper to convert {@link PermissionEntity} entities to {@link PermissionDTO}
   */
  public GetPermissionByApplicationIdQueryHandler(PermissionEntityRepository permissionRepository, PermissionMapper permissionMapper) {

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
    Set<PermissionEntity> permissionList = permissionRepository.findAll()
            .stream()
            .filter(permission -> resolveCondition(permission, query)
                    && (permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE)))
            .collect(Collectors.toSet());

    List<PermissionDTO> permissionDTO = permissionList.stream()
            .map(permissionMapper::mapToDTO)
            .toList();
    return new ResponseEntity<>(permissionDTO, HttpStatus.OK);
  }

  private boolean resolveCondition(PermissionEntity permission, GetPermissionByApplicationIdQuery query) {

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