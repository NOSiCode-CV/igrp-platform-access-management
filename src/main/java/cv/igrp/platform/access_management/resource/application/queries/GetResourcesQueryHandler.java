package cv.igrp.platform.access_management.resource.application.queries;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_RESOURCE;

/**
 * Handles queries for retrieving a list of {@link ResourceDTO}s filtered by optional criteria
 * such as name, application ID, type, and external ID.
 * <p>
 * This handler dynamically builds a JPA {@link Specification} to fetch matching
 * {@link ResourceEntity} entities and maps them to DTOs for client consumption.
 * </p>
 */
@Component
public class GetResourcesQueryHandler implements QueryHandler<GetResourcesQuery, ResponseEntity<List<ResourceDTO>>>{

  private static final Logger logger = LoggerFactory.getLogger(GetResourcesQueryHandler.class);

  private final ResourceEntityRepository resourceRepository;
  private final ResourceMapper resourceMapper;

  /**
   * Constructs a {@code GetResourcesQueryHandler} with the required dependencies.
   *
   * @param resourceRepository the repository used to retrieve resource entities
   * @param resourceMapper     the mapper used to convert entities to DTOs
   */
  public GetResourcesQueryHandler(
          ResourceEntityRepository resourceRepository,
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
    logger.info("Handling GetResourcesQuery: name={}, applicationCode={}, type={}, externalId={}",
            query.getName(), query.getApplicationCode(), query.getType(), query.getExternalID());

    Specification<ResourceEntity> spec = buildSpecification(
            query.getName(), query.getType(), query.getExternalID(), query.getApplicationCode());

    List<ResourceDTO> resources = resourceRepository.findAll(spec)
            .stream()
            .filter(resourceEntity -> !Objects.equals(resourceEntity.getName(), IGRP_RESOURCE))
            .map(resourceMapper::toDto)
            .toList();

    return ResponseEntity.ok(resources);
  }

  /**
   * Builds a dynamic {@link Specification} for querying {@link ResourceEntity} entities based
   * on the provided filters.
   *
   * @param name         optional name filter
   * @param type         optional resource type name, must be a valid {@link ResourceType}
   * @param externalId   optional exact external ID match
   * @return a composed {@link Specification} used to query the resource repository
   */
  private Specification<ResourceEntity> buildSpecification(
          String name,
          String type, String externalId,
          String applicationCode
  ) {

    Specification<ResourceEntity> spec = Specification.anyOf();
    if (name != null && !name.isBlank()) {
      spec = spec.and((root, query, cb) ->
              cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
      );
    }

    if (applicationCode != null) {

      spec = spec.and((root, query, cb) -> {
                Join<MenuEntryEntity, ApplicationEntity> applicationJoin = root.join("applicationId");
                return cb.equal(applicationJoin.get("code"), applicationCode);
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

    // Excluded deleted resources
    spec = spec.and((root, _, cb) ->
            cb.notEqual(root.get("status"), Status.DELETED)
    );

    return spec;
  }

}