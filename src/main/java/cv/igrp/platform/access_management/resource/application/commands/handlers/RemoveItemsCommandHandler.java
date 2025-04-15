package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.RemoveItemsCommand;

import java.util.List;


@Service
public class RemoveItemsCommandHandler implements CommandHandler<RemoveItemsCommand, ResponseEntity<ResourceDTO>> {

   private ResourceRepository resourceRepository;
   private ResourceMapper resourceMapper;

   public RemoveItemsCommandHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(RemoveItemsCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + command.getId()));
              });
      List<ResourceItem> updatedItems = resource.getItems().stream()
              .filter(item -> !command.getRemoveItemsRequest().contains(item.getId()))
              .toList();
      resource.setItems(updatedItems);
      return ResponseEntity.ok(resourceMapper.toDto(resourceRepository.save(resource)));
   }

}