package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving all active or inactive {@link RoleDTO} instances
 * associated with a given {@link PermissionEntity} ID.
 *
 * <p>This handler:
 * <ul>
 *   <li>Fetches the {@link PermissionEntity} by its ID, ensuring it is not marked as {@link Status#DELETED}.</li>
 *   <li>Extracts the related {@link RoleEntity}s and filters only those with status {@link Status#ACTIVE} or {@link Status#INACTIVE}.</li>
 *   <li>Maps the filtered roles to their corresponding {@link RoleDTO} representations.</li>
 * </ul>
 *
 * <p>If the permission is not found or is marked as {@link Status#DELETED}, a {@link IgrpResponseStatusException}
 * with {@link HttpStatus#NOT_FOUND} is thrown.
 *
 * @see GetRolesByPermissionIDQuery
 * @see PermissionEntityRepository
 * @see RoleMapper
 * @see RoleDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class GetRolesByPermissionIDQueryHandler implements QueryHandler<GetRolesByPermissionIDQuery, ResponseEntity<List<RoleDTO>>>{

  private final RoleMapper roleMapper;
  private final PermissionEntityRepository permissionRepository;

  /**
   * Constructs a new instance of the handler with required dependencies.
   *
   * @param roleMapper           Mapper to convert {@link RoleEntity} entities to {@link RoleDTO}.
   * @param permissionRepository Repository used to retrieve {@link PermissionEntity} entities.
   */
  public GetRolesByPermissionIDQueryHandler(RoleMapper roleMapper, PermissionEntityRepository permissionRepository) {

    this.roleMapper = roleMapper;
    this.permissionRepository = permissionRepository;
  }

  /**
   * Handles the query by retrieving and returning all non-deleted roles associated with
   * the given permission ID.
   *
   * @param query the query containing the permission ID.
   * @return a {@link ResponseEntity} containing a list of {@link RoleDTO}s.
   * @throws IgrpResponseStatusException if the permission is not found or is deleted.
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<List<RoleDTO>> handle(GetRolesByPermissionIDQuery query) {
    log.info("Get Roles with Permission name {}", query.getName());
    PermissionEntity permissionFound = permissionRepository.findByName(query.getName())
            .filter(permission -> permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE))
            .orElseThrow(() -> {
              log.warn("Get Roles with Permission name {}", query.getName());
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND, "Get Role By Permission name", "Permission with name: " + query.getName() + " not found."
              );
            });
    Set<RoleEntity> roles = Optional.ofNullable(permissionFound.getRoles())
            .orElse(Collections.emptySet());

    List<RoleDTO> response = roles
            .stream()
            .filter(role -> role.getStatus().equals(Status.ACTIVE) || role.getStatus().equals(Status.INACTIVE))
            .map(roleMapper::mapToDto)
            .toList();

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

}