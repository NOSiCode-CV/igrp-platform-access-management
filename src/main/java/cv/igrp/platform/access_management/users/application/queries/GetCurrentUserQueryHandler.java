package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

import java.util.Optional;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

/**
 * Handles the {@link GetCurrentUserQuery} by retrieving the currently authenticated user's information.
 * <p>
 * This handler uses the {@link AuthenticationHelper} to determine the current user's username,
 * looks up the user in the {@link IGRPUserEntityRepository}, and maps the domain model to a {@link IGRPUserDTO}.
 * </p>
 *
 * <p>If the user is not found, a 404 response is returned.</p>
 *
 */
@Component
public class GetCurrentUserQueryHandler implements QueryHandler<GetCurrentUserQuery, ResponseEntity<IGRPUserDTO>>{

  private static final Logger logger = LoggerFactory.getLogger(GetCurrentUserQueryHandler.class);

  private final IGRPUserEntityRepository igrpUserRepository;
  private final IGRPUserMapper userMapper;
  private final AuthenticationHelper authenticationHelper;

  /**
   * Constructs the handler with necessary dependencies.
   *
   * @param igrpUserRepository     the repository to retrieve user data
   * @param userMapper             the mapper to convert entities to DTOs
   * @param authenticationHelper   the helper to access the authenticated user context
   */
  public GetCurrentUserQueryHandler(
          IGRPUserEntityRepository igrpUserRepository,
          IGRPUserMapper userMapper, AuthenticationHelper
                  authenticationHelper) {
    this.igrpUserRepository = igrpUserRepository;
    this.userMapper = userMapper;
    this.authenticationHelper = authenticationHelper;
  }

  /**
   * Handles the {@link GetCurrentUserQuery} by retrieving the current user based on the authentication context.
   *
   * @param query the query input (unused in this case)
   * @return a {@link ResponseEntity} containing {@link IGRPUserDTO} if found, or 404 otherwise
   */
  @IgrpQueryHandler
  public ResponseEntity<IGRPUserDTO> handle(GetCurrentUserQuery query) {
    String userId;
    try {
        userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
    } catch (NumberFormatException e) {
        logger.error("Invalid token sub: expected an integer ID but got '{}'", authenticationHelper.getSub());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    logger.info("Fetching current user with id: {}", userId);

    Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findByIdWithRolesAndPermissions(userId);
    if (optionalUser.isEmpty()) {
      logger.warn("No user found with id: {}", userId);
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    IGRPUserDTO dto = userMapper.toDto(optionalUser.get());
    logger.info("User found. ID: {}, Email: {}", dto.getId(), dto.getEmail());

    return ResponseEntity.ok(dto);
  }

}