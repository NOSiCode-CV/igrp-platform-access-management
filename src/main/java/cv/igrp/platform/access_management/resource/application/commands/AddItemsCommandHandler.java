package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler responsible for adding {@link ResourceItemEntity}s to an existing {@link ResourceEntity}.
 * <p>
 * This handler retrieves the resource by ID, maps each incoming to a domain
 * {@link ResourceItemEntity}, associates them with their corresponding {@link PermissionEntity}s, and appends them
 * to the resource's item list.
 * </p>
 * <p>
 * If the resource or any permission is not found, an {@link IgrpResponseStatusException} is thrown with a detailed problem description.
 * </p>
 * The updated resource is persisted and returned as a {@link ResourceDTO}.
 *
 * @see AddItemsCommand
 * @see ResourceEntity
 * @see ResourceItemEntity
 * @see PermissionEntity
 */
@Component
public class AddItemsCommandHandler implements CommandHandler<AddItemsCommand, ResponseEntity<ResourceDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(AddItemsCommandHandler.class);

   private final ResourceEntityRepository resourceRepository;
   private final PermissionEntityRepository permissionRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs the {@code AddItemsCommandHandler} with required dependencies.
    *
    * @param resourceRepository     the repository used to fetch and persist {@link ResourceEntity} entities
    * @param resourceMapper         the mapper used to convert DTOs and domain models
    * @param permissionRepository   the repository used to fetch {@link PermissionEntity} entities
    */
   public AddItemsCommandHandler(
           ResourceEntityRepository resourceRepository,
           ResourceMapper resourceMapper,
           PermissionEntityRepository permissionRepository) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
      this.permissionRepository = permissionRepository;
   }

   /**
    * Handles the {@link AddItemsCommand} by appending mapped {@link ResourceItemEntity}s to a {@link ResourceEntity}.
    * <p>
    * - If the resource ID does not exist, a 404 response is returned. <br>
    * - If the resource’s item list is null, it is initialized before appending.
    * </p>
    *
    * @param command the command containing the resource ID and the list of items to add
    * @return {@link ResponseEntity} containing the updated {@link ResourceDTO}
    * @throws IgrpResponseStatusException if the resource or any referenced permission is not found
    */
   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(AddItemsCommand command) {
      logger.info("Handling AddItemsCommand for resource name: {}", command.getName());

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
              .orElseThrow(() -> {logger.warn("Resource not found with name: {}", command.getName());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with name: " + command.getName());
              });
      List<ResourceItemEntity> items = command.getResourceitemdto().stream()
              .map(dto -> {
                 PermissionEntity permission = permissionRepository.findByNameAndStatusNot(dto.getPermissionName(), Status.DELETED)
                         .orElseThrow(() -> {
                            logger.warn("Permission not found with name: {}", dto.getPermissionName());
                            return IgrpResponseStatusException.of(
                                    HttpStatus.NOT_FOUND,
                                    "Permission not found",
                                    "Permission not found with name: " + dto.getPermissionName());
                         });
                 return resourceMapper.toItemEntity(dto, resource, permission);
              })
              .toList();

      if (resource.getItems() == null) {
         logger.warn("Resource with name={} has null item list. Initializing new list.", resource.getName());
         resource.setItems(new ArrayList<>());
      }

      resource.getItems().addAll(items);
      var resourceSaved = resourceRepository.save(resource);

      logger.info("Added {} item(s) to resource name={}", items.size(), resource.getName());

      return ResponseEntity.ok(resourceMapper.toDto(resourceSaved));
   }

}