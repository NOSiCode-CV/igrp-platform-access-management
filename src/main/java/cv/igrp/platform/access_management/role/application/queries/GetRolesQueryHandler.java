package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
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
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving all active or inactive {@link RoleDTO} entries.
 *
 * <p>This handler performs the following:</p>
 * <ul>
 *   <li>Retrieves all {@link RoleEntity} entities from the database that have a status of {@link Status#ACTIVE} or {@link Status#INACTIVE}</li>
 *   <li>Maps each entity to its {@link RoleDTO} representation using {@link RoleMapper}</li>
 *   <li>Returns the results wrapped in a {@link ResponseEntity} with HTTP status {@link HttpStatus#OK}</li>
 * </ul>
 *
 * @see GetRolesQuery
 * @see RoleEntityRepository
 * @see RoleMapper
 * @see RoleDTO
 * @see Status
 */
@Slf4j
@Component
public class GetRolesQueryHandler implements QueryHandler<GetRolesQuery, ResponseEntity<List<RoleDTO>>>{

  private final RoleEntityRepository roleRepository;
  private final RoleMapper roleMapper;

  /**
   * Constructs a new instance of {@code GetRolesQueryHandler} with the required dependencies.
   *
   * @param roleRepository the repository used to fetch roles from the database
   * @param roleMapper     the mapper used to convert {@link RoleEntity} entities to {@link RoleDTO}
   */
  public GetRolesQueryHandler(RoleEntityRepository roleRepository, RoleMapper roleMapper) {

    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
  }

  /**
   * Handles the {@link GetRolesQuery} to retrieve all active or inactive roles.
   *
   * <ul>
   *     <li>Queries the repository for roles with {@link Status#ACTIVE} or {@link Status#INACTIVE}</li>
   *     <li>Maps each {@link RoleEntity} entity to a {@link RoleDTO}</li>
   *     <li>Returns the list wrapped in a {@link ResponseEntity} with status 200 (OK)</li>
   * </ul>
   *
   * @param query the query object (currently unused, but may include filters in the future)
   * @return a {@link ResponseEntity} containing the list of {@link RoleDTO}
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<List<RoleDTO>> handle(GetRolesQuery query) {
    log.info("Get Roles");
    Status active = Status.ACTIVE;
    Status inactive = Status.INACTIVE;
    List<RoleEntity> allRoles = roleRepository.findByStatusIn(List.of(active, inactive));
    List<RoleDTO> collectedRole = allRoles.stream()
            .map(roleMapper::mapToDto)
            .toList();
    return new ResponseEntity<>(collectedRole, HttpStatus.OK);
  }

}