package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.AddItemsCommand;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

import java.util.List;

@Service
public class AddItemsCommandHandler implements CommandHandler<AddItemsCommand, ResponseEntity<ResourceDTO>> {

   private ResourceRepository resourceRepository;
   private ResourceMapper resourceMapper;

   public AddItemsCommandHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(AddItemsCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
      List<ResourceItem> items = command.getResourceitemdto().stream()
              .map(dto -> {
                  return resourceMapper.toItemEntity(dto, resource);
              })
              .toList();
      resource.getItems().addAll(items);
      return ResponseEntity.ok(resourceMapper.toDto(resourceRepository.save(resource)));
   }

}
