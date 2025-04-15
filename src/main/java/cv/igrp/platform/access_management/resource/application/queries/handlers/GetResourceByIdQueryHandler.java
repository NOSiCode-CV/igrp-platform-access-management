package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourceByIdQuery;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Service
public class GetResourceByIdQueryHandler implements QueryHandler<GetResourceByIdQuery, ResponseEntity<ResourceDTO>>{

   private ResourceRepository resourceRepository;
   private ResourceMapper resourceMapper;

   public GetResourceByIdQueryHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<ResourceDTO> handle(GetResourceByIdQuery query) {
      Resource resource = resourceRepository.findById(query.getId())
              .orElseThrow(() -> new EntityNotFoundException("Resource not found with id " + query.getId()));
      return ResponseEntity.ok(resourceMapper.toDto(resource));
   }

}