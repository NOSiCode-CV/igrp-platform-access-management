package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourceByIdQuery;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

/**
 * Query handler responsible for retrieving a {@link ResourceDTO} by its unique identifier.
 * <p>
 * This handler uses the {@link ResourceRepository} to fetch the {@link Resource} entity,
 * and converts it to a DTO using {@link ResourceMapper}. If the resource is not found,
 * it throws a structured {@link IgrpResponseStatusException} describing the error.
 * </p>
 *
 * @see Resource
 * @see ResourceDTO
 * @see GetResourceByIdQuery
 * @see IgrpResponseStatusException
 */
@Service
public class GetResourceByIdQueryHandler implements
        QueryHandler<GetResourceByIdQuery, ResponseEntity<ResourceDTO>>{

   private static final Logger logger =
           LoggerFactory.getLogger(GetResourceByIdQueryHandler.class);

   private final ResourceRepository resourceRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a new {@code GetResourceByIdQueryHandler}.
    *
    * @param resourceRepository the repository used to retrieve {@link Resource} entities
    * @param resourceMapper     the mapper used to convert {@link Resource} to {@link ResourceDTO}
    */
   public GetResourceByIdQueryHandler(
           ResourceRepository resourceRepository,
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
      Integer resourceId = query.getId();
      logger.info("Fetching resource with ID: {}", resourceId);

      Resource resource = resourceRepository.findById(resourceId)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with ID: {}", resourceId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with id: " + resourceId);
              });

      logger.info("Resource found with ID: {}", resourceId);
      return ResponseEntity.ok(resourceMapper.toDto(resource));
   }
}