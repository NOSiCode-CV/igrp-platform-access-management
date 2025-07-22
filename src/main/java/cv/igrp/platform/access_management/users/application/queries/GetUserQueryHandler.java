package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Query handler responsible for retrieving a single {@link IGRPUserEntity} entity by its ID.
 * <p>
 * This handler processes {@link GetUserQuery} instances and performs the following steps:
 * <ul>
 *   <li>Validates that the provided query ID is not {@code null}.</li>
 *   <li>Attempts to retrieve the user from the {@link IGRPUserEntityRepository}.</li>
 *   <li>Maps the retrieved {@link IGRPUserEntity} entity to a {@link IGRPUserDTO} using the {@link IGRPUserMapper}.</li>
 *   <li>Returns a {@link ResponseEntity} containing the user DTO and HTTP status {@code 200 OK}.</li>
 * </ul>
 * <p>
 * If the ID is {@code null}, a {@code 400 Bad Request} response is returned with no body.
 * If no user is found for the provided ID, an {@link IgrpResponseStatusException} is thrown.
 *
 */
@Component
public class GetUserQueryHandler implements QueryHandler<GetUserQuery, ResponseEntity<IGRPUserDTO>>{

  private static final Logger logger = LoggerFactory.getLogger(GetUserQueryHandler.class);

  private final IGRPUserEntityRepository igrpUserRepository;
  private final IGRPUserMapper igrpUserMapper;

  /**
   * Constructs a new {@code GetUserQueryHandler} with required dependencies.
   *
   * @param igrpUserRepository the repository used to retrieve users
   * @param igrpUserMapper     the mapper used to convert entities to DTOs
   */
  public GetUserQueryHandler(
          IGRPUserEntityRepository igrpUserRepository,
          IGRPUserMapper igrpUserMapper) {
    this.igrpUserRepository = igrpUserRepository;
    this.igrpUserMapper = igrpUserMapper;
  }

  /**
   * Handles the {@link GetUserQuery} by retrieving the user and returning a corresponding DTO.
   *
   * @param query the query containing the user ID to fetch
   * @return a {@link ResponseEntity} with the user DTO and status {@code 200 OK},
   *         or {@code 400 Bad Request} if the query ID is {@code null}
   * @throws IgrpResponseStatusException if no user is found for the given ID
   */
  @IgrpQueryHandler
  public ResponseEntity<IGRPUserDTO> handle(GetUserQuery query) {
    Integer userId = query.getId();

    if(userId == null) {
      logger.warn("GetUserQuery received with null ID");
      return ResponseEntity.badRequest().build();
    }

    logger.info("Fetching user with id={}", userId);

    IGRPUserEntity user = igrpUserRepository.findById(query.getId())
            .orElseThrow(() -> {
              logger.warn("User not found with id={}", userId);
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND,
                      "Invalida User id",
                      "User not found with id: " + userId);
            });

    IGRPUserDTO dto = igrpUserMapper.toDto(user);

    logger.info("Successfully retrieved user: id={}, username={}", dto.getId(), dto.getUsername());

    return ResponseEntity.ok(dto);
  }

}