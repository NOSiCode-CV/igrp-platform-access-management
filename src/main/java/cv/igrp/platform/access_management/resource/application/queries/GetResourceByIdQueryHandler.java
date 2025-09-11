package cv.igrp.platform.access_management.resource.application.queries;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

/**
 * Query handler responsible for retrieving a {@link ResourceDTO} by its unique identifier.
 * <p>
 * This handler uses the {@link ResourceEntityRepository} to fetch the {@link ResourceEntity} entity,
 * and converts it to a DTO using {@link ResourceMapper}. If the resource is not found,
 * it throws a structured {@link IgrpResponseStatusException} describing the error.
 * </p>
 *
 * @see ResourceEntity
 * @see ResourceDTO
 * @see GetResourceByIdQuery
 * @see IgrpResponseStatusException
 */
@Component
public class GetResourceByIdQueryHandler implements QueryHandler<GetResourceByIdQuery, ResponseEntity<ResourceDTO>>{

  private static final Logger logger = LoggerFactory.getLogger(GetResourceByIdQueryHandler.class);

  private final ResourceEntityRepository resourceRepository;
  private final ResourceMapper resourceMapper;

  /**
   * Constructs a new {@code GetResourceByIdQueryHandler}.
   *
   * @param resourceRepository the repository used to retrieve {@link ResourceEntity} entities
   * @param resourceMapper     the mapper used to convert {@link ResourceEntity} to {@link ResourceDTO}
   */
  public GetResourceByIdQueryHandler(
          ResourceEntityRepository resourceRepository,
          ResourceMapper resourceMapper) {
    this.resourceRepository = resourceRepository;
    this.resourceMapper = resourceMapper;
  }

  /**
   * Handles the retrieval of a resource by its ID.
   *
   * @param query the {@link GetResourceByIdQuery} containing the resource ID to retrieve
   * @return a {@link ResponseEntity} containing the mapped {@link ResourceDTO}
   * @throws IgrpResponseStatusException if the resource with the specified ID is not found
   */
  @IgrpQueryHandler
  public ResponseEntity<ResourceDTO> handle(GetResourceByIdQuery query) {
    String resourceName = query.getName();
    logger.info("Fetching resource with name: {}", resourceName);

    ResourceEntity resource = resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)
            .orElseThrow(() -> {
              logger.warn("Resource not found with name: {}", resourceName);
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND,
                      "Resource not found",
                      "Resource not found with name: " + resourceName);
            });

    logger.info("Resource found with name: {}", resourceName);
    return ResponseEntity.ok(resourceMapper.toDto(resource));
  }

}