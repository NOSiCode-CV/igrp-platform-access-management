package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.UpdateResourceCommand;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Service
public class UpdateResourceCommandHandler implements CommandHandler<UpdateResourceCommand, ResponseEntity<ResourceDTO>> {

   private ResourceRepository resourceRepository;
   private ResourceMapper resourceMapper;

   public UpdateResourceCommandHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(UpdateResourceCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Resource not found with id " + command.getId()));
      resource.setName(command.getResourcedto().getName());
      resource.setType(command.getResourcedto().getType());
      return ResponseEntity.ok(resourceMapper.toDto(resourceRepository.save(resource)));
   }

}