package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourcesQuery;
import java.util.List;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Service
public class GetResourcesQueryHandler implements QueryHandler<GetResourcesQuery, ResponseEntity<List<ResourceDTO>>>{

   private ResourceRepository resourceRepository;
   private ResourceMapper resourceMapper;

   public GetResourcesQueryHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<ResourceDTO>> handle(GetResourcesQuery query) {
      Specification<Resource> spec = buildSpecification(query.getName(), query.getApplicationId());
      List<ResourceDTO> resources = resourceRepository.findAll(spec).stream().map(resourceMapper::toDto).toList();
      return ResponseEntity.ok(resources);
   }

   private Specification<Resource> buildSpecification(String name, Integer applicationId) {
      Specification<Resource> spec = Specification.where(null);
      if (name != null && !name.isEmpty()) {
         spec = spec.and((root, query, cb) ->
                 cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
         );
      }
      if (applicationId != null) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("applicationId").get("id"), applicationId)
         );
      }
      return spec;
   }

}