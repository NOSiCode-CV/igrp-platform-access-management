package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.AddItemsCommand;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

import java.util.List;

@Service
public class AddItemsCommandHandler implements CommandHandler<AddItemsCommand, ResponseEntity<ResourceDTO>> {

   private ResourceRepository resourceRepository;
   private PermissionRepository permissionRepository;
   private ResourceMapper resourceMapper;

   public AddItemsCommandHandler(ResourceRepository resourceRepository, ResourceMapper resourceMapper, PermissionRepository permissionRepository) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
      this.permissionRepository = permissionRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(AddItemsCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + command.getId()));
              });
      List<ResourceItem> items = command.getResourceitemdto().stream()
              .map(dto -> {
                  Permission permission = permissionRepository.findById(dto.getPermissionId())
                          .orElseThrow(() -> {
                              return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Permission not found", "Permission not found with id: " + dto.getPermissionId()));
                          });
                  return resourceMapper.toItemEntity(dto, resource, permission);
              })
              .toList();
      resource.getItems().addAll(items);
      return ResponseEntity.ok(resourceMapper.toDto(resourceRepository.save(resource)));
   }

}
