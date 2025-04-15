package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
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
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + query.getId()));
              });
      return ResponseEntity.ok(resourceMapper.toDto(resource));
   }

}