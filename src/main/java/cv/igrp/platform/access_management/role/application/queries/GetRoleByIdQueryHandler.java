package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query handler responsible for retrieving a single {@link RoleDTO} based on its unique identifier.
 *
 * <p>This handler performs the following actions:</p>
 * <ul>
 *   <li>Looks up a {@link RoleEntity} by its ID using {@link RoleEntityRepository}, ignoring those marked as {@link Status#DELETED}</li>
 *   <li>If found, the role is mapped to a {@link RoleDTO} via {@link RoleMapper}</li>
 *   <li>The mapped result is returned in a {@link ResponseEntity} with HTTP status {@link HttpStatus#OK}</li>
 *   <li>If the role is not found or is deleted, an {@link IgrpResponseStatusException} is thrown with HTTP status {@link HttpStatus#NOT_FOUND}</li>
 * </ul>
 *
 * @see GetRoleByIdQuery
 * @see RoleEntityRepository
 * @see RoleMapper
 * @see RoleDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class GetRoleByIdQueryHandler implements QueryHandler<GetRoleByIdQuery, ResponseEntity<RoleDTO>>{

  private final RoleEntityRepository roleRepository;
  private final RoleMapper roleMapper;

  /**
   * Constructs a new instance of {@code GetRoleByIdQueryHandler} with the required dependencies.
   *
   * @param roleRepository the repository used to access role entities
   * @param roleMapper the mapper used to convert {@link RoleEntity} entities to {@link RoleDTO}
   */
  public GetRoleByIdQueryHandler(RoleEntityRepository roleRepository, RoleMapper roleMapper) {
    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
  }

  /**
   * Handles the incoming {@link GetRoleByIdQuery} by retrieving the role with the specified ID from the repository.
   * <ul>
   *   <li>If the role is found and is not marked as {@link Status#DELETED}, it is returned as a {@link RoleDTO}.</li>
   *   <li>If the role is not found or is marked as deleted, an exception is thrown with a {@code 404 NOT FOUND} status.</li>
   * </ul>
   *
   * @param query the query containing the ID of the role to retrieve
   * @return a {@link ResponseEntity} containing the {@link RoleDTO} with status {@link HttpStatus#OK}
   * @throws IgrpResponseStatusException if the role is not found or is marked as {@code DELETED}
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<RoleDTO> handle(GetRoleByIdQuery query) {
    log.info("Get Role with id {}.", query.getId());
    RoleEntity foundRole = roleRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
            .orElseThrow(() -> {
              log.warn("Role with id {} not found.", query.getId());
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND, "Get Role By Id", "Role with id: " + query.getId() + " not found."
              );
            });
    RoleDTO response = roleMapper.mapToDto(foundRole);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

}