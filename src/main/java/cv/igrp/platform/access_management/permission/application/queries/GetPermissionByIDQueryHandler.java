package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving a single {@link PermissionEntity} by its ID,
 * provided it is not marked as {@link Status#DELETED}.
 *
 * <p>This handler looks up the permission by ID using {@link PermissionEntityRepository},
 * and maps the entity to a {@link PermissionDTO} using {@link PermissionMapper}.
 * If the permission is not found or is deleted, an {@link IgrpResponseStatusException}
 * is thrown with a 404 Not Found status.
 *
 * @see GetPermissionByIDQuery
 * @see PermissionEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class GetPermissionByIDQueryHandler implements QueryHandler<GetPermissionByIDQuery, ResponseEntity<PermissionDTO>>{

  private final PermissionEntityRepository permissionRepository;
  private final PermissionMapper permissionMapper;

  /**
   * Constructs the handler with required dependencies.
   *
   * @param permissionRepository repository for querying {@link PermissionEntity} entities
   * @param permissionMapper mapper to convert {@link PermissionEntity} to {@link PermissionDTO}
   */
  public GetPermissionByIDQueryHandler(PermissionEntityRepository permissionRepository, PermissionMapper permissionMapper) {

    this.permissionRepository = permissionRepository;
    this.permissionMapper = permissionMapper;
  }

  /**
   * Handles the retrieval of a permission by its ID.
   * Only permissions not marked as {@link Status#DELETED} will be returned.
   *
   * @param query the query object containing the target permission ID
   * @return a {@link ResponseEntity} containing the corresponding {@link PermissionDTO}
   * @throws IgrpResponseStatusException if the permission is not found or deleted
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<PermissionDTO> handle(GetPermissionByIDQuery query) {
    log.info("Get Permission with ID {}", query.getId());
    PermissionEntity foundPermission = permissionRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
            .orElseThrow(() -> {
              log.warn("Permission with ID {} not found.", query.getId());
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND, "Get Permission By ID", "Permission with id: " + query.getId() + " not found."
              );
            });
    PermissionDTO responseDto = permissionMapper.mapToDTO(foundPermission);
    return new ResponseEntity<>(responseDto, HttpStatus.OK);
  }

}