package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourcesQuery;
import java.util.List;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

/**
 * Handles queries for retrieving a list of {@link ResourceDTO}s filtered by optional criteria
 * such as name, application ID, type, and external ID.
 * <p>
 * This handler dynamically builds a JPA {@link Specification} to fetch matching
 * {@link Resource} entities and maps them to DTOs for client consumption.
 * </p>
 */
@Service
public class GetResourcesQueryHandler implements
        QueryHandler<GetResourcesQuery, ResponseEntity<List<ResourceDTO>>>{

   private static final Logger logger =
           LoggerFactory.getLogger(GetResourcesQueryHandler.class);

   private final ResourceRepository resourceRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a {@code GetResourcesQueryHandler} with the required dependencies.
    *
    * @param resourceRepository the repository used to retrieve resource entities
    * @param resourceMapper     the mapper used to convert entities to DTOs
    */
   public GetResourcesQueryHandler(
           ResourceRepository resourceRepository,
           ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   /**
    * Handles the {@link GetResourcesQuery} by dynamically constructing query specifications,
    * retrieving matching resources, and returning them as a list of {@link ResourceDTO}.
    *
    * @param query the query object containing optional filter parameters
    * @return a {@link ResponseEntity} with HTTP 200 OK and the list of matched resource DTOs
    */
   @IgrpQueryHandler
   public ResponseEntity<List<ResourceDTO>> handle(GetResourcesQuery query) {
      logger.info("Handling GetResourcesQuery: name={}, applicationId={}, type={}, externalId={}",
              query.getName(), query.getApplicationId(), query.getType(), query.getExternalID());

      Specification<Resource> spec = buildSpecification(
              query.getName(), query.getApplicationId(), query.getType(), query.getExternalID());

      List<ResourceDTO> resources = resourceRepository.findAll(spec)
              .stream()
              .map(resourceMapper::toDto)
              .toList();

      return ResponseEntity.ok(resources);
   }

   /**
    * Builds a dynamic {@link Specification} for querying {@link Resource} entities based
    * on the provided filters.
    *
    * @param name         optional name filter
    * @param applicationId optional application ID to match
    * @param type         optional resource type name, must be a valid {@link ResourceType}
    * @param externalId   optional exact external ID match
    * @return a composed {@link Specification} used to query the resource repository
    */
   private Specification<Resource> buildSpecification(
           String name, Integer applicationId,
           String type, String externalId) {

      Specification<Resource> spec = Specification.where(null);
      if (name != null && !name.isBlank()) {
         spec = spec.and((root, query, cb) ->
                 cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
         );
      }
      if (applicationId != null) {

         spec = spec.and((root, query, cb) -> {
                    Join<MenuEntry, Application> applicationJoin = root.join("applicationId");
                    return cb.equal(applicationJoin.get("id"), applicationId);
                 }
         );
      }
      if (type != null) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("type"), ResourceType.valueOf(type))
         );
      }
      if (externalId != null) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("externalId"), externalId)
         );
      }
      return spec;
   }
}