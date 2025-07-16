package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving all active (non-deleted) {@link PermissionDTO}s associated with a specific {@link RoleEntity} ID.
 *
 * <p>This handler performs the following operations:</p>
 * <ul>
 *   <li>Validates that the {@link RoleEntity} exists and is not marked as {@link Status#DELETED}</li>
 *   <li>Filters out permissions that are marked as {@link Status#DELETED}</li>
 *   <li>Maps the remaining {@link PermissionEntity} entities to {@link PermissionDTO} instances</li>
 *   <li>Returns the result wrapped in a {@link ResponseEntity} with status {@link HttpStatus#OK}</li>
 * </ul>
 *
 * <p>If the role is not found or is marked as deleted, an {@link IgrpResponseStatusException} is thrown.</p>
 *
 * @see cv.igrp.platform.access_management.role.application.queries.queries.GetPermissionsByRoleIdQuery
 * @see PermissionDTO
 * @see RoleEntityRepository
 * @see PermissionMapper
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class GetPermissionsByRoleIdQueryHandler implements QueryHandler<GetPermissionsByRoleIdQuery, ResponseEntity<List<PermissionDTO>>>{

  private final RoleEntityRepository roleRepository;
  private final PermissionMapper permissionMapper;

  /**
   * Constructs the handler with necessary dependencies.
   *
   * @param roleRepository   repository to retrieve roles from the database
   * @param permissionMapper mapper to convert permission entities to DTOs
   */
  public GetPermissionsByRoleIdQueryHandler(RoleEntityRepository roleRepository, PermissionMapper permissionMapper) {

    this.roleRepository = roleRepository;
    this.permissionMapper = permissionMapper;
  }

  /**
   * Handles the query to fetch permissions for a given role ID.
   *
   * @param query the query containing the role ID
   * @return a list of mapped {@link PermissionDTO} objects excluding those with status {@link Status#DELETED}
   * @throws IgrpResponseStatusException if the role is not found or is marked as deleted
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<List<PermissionDTO>> handle(GetPermissionsByRoleIdQuery query) {
    log.info("Get Permissions from Role with id {}.", query.getId());
    RoleEntity foundRole = roleRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
            .orElseThrow(() -> {
              log.warn("Role with id {} not found.", query.getId());
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND, "Get Permission By Role ID", "Role with id: " + query.getId() + " not found."
              );
            });
    List<PermissionDTO> permissionList = foundRole.getPermissions()
            .stream()
            .filter(permission -> !permission.getStatus().equals(Status.DELETED))
            .map(permissionMapper::mapToDTO)
            .toList();
    return new ResponseEntity<>(permissionList, HttpStatus.OK);
  }

}