package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

/**
 * Handler responsible for processing the {@link GetUserRolesQuery} and retrieving the list of roles assigned to a specific user.
 * <p>
 * This handler performs the following steps:
 * <ul>
 *     <li>Fetches the user entity by the provided user ID.</li>
 *     <li>If the user is not found, throws {@link IgrpResponseStatusException}.</li>
 *     <li>Retrieves the user's roles and maps them to {@link RoleDTO} instances using {@link RoleMapper}.</li>
 *     <li>Filters out any {@code null} values returned by the mapper to ensure a clean result.</li>
 *     <li>Returns the resulting list of {@link RoleDTO} wrapped in a {@link ResponseEntity} with HTTP status 200 (OK).</li>
 * </ul>
 *
 * @see GetUserRolesQuery
 * @see RoleDTO
 * @see IGRPUserEntity
 * @see IGRPUserEntityRepository
 * @see RoleMapper
 */
@Component
public class GetUserRolesQueryHandler implements QueryHandler<GetUserRolesQuery, ResponseEntity<List<RoleDTO>>>{

  private static final Logger logger = LoggerFactory.getLogger(GetUserRolesQueryHandler.class);

  private final IGRPUserEntityRepository userRepository;
  private final RoleMapper roleMapper;
  private final UserRoleAssignmentRepository userRoleAssignmentRepository;

  /**
   * Constructs the handler with required dependencies.
   *
   * @param userRepository the repository used to retrieve user data
   * @param roleMapper     the mapper used to convert {@link RoleEntity} entities to {@link RoleDTO}
   * @param userRoleAssignmentRepository the repository for user role assignments
   */
  public GetUserRolesQueryHandler(
          IGRPUserEntityRepository userRepository,
          RoleMapper roleMapper,
          UserRoleAssignmentRepository userRoleAssignmentRepository) {
    this.userRepository = userRepository;
    this.roleMapper = roleMapper;
    this.userRoleAssignmentRepository = userRoleAssignmentRepository;
  }

  /**
   * Handles the {@link GetUserRolesQuery} by retrieving and returning the roles of the specified user.
   *
   * @param query the query containing the user ID
   * @return a {@link ResponseEntity} containing the list of {@link RoleDTO}, or an empty list if the user has no roles
   * @throws IgrpResponseStatusException if the user with the specified ID is not found
   */
  @IgrpQueryHandler
  public ResponseEntity<List<RoleDTO>> handle(GetUserRolesQuery query) {
    String id = query.getId();

    logger.info("Fetching roles for user ID={}", id);

    IGRPUserEntity user = userRepository.findById(id)
            .orElseThrow(() -> {
              logger.warn("User not found with ID={}", id);
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND,
                      "Invalid User",
                      "User not found with ID: " + id);
            });

    List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findActiveByUserId(id);

    List<RoleDTO> result = assignments.stream()
            .map(roleMapper::mapToDto)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    logger.info("User ID={} has {} role(s)", id, result.size());

    return ResponseEntity.ok(result);
  }

}