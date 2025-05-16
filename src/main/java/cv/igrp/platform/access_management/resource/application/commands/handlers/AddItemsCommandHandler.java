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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.AddItemsCommand;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * Command handler responsible for adding {@link ResourceItem}s to an existing {@link Resource}.
 * <p>
 * This handler retrieves the resource by ID, maps each incoming to a domain
 * {@link ResourceItem}, associates them with their corresponding {@link Permission}s, and appends them
 * to the resource's item list.
 * </p>
 * <p>
 * If the resource or any permission is not found, an {@link IgrpResponseStatusException} is thrown with a detailed problem description.
 * </p>
 * The updated resource is persisted and returned as a {@link ResourceDTO}.
 *
 * @see AddItemsCommand
 * @see Resource
 * @see ResourceItem
 * @see Permission
 */
@Service
public class AddItemsCommandHandler implements CommandHandler<AddItemsCommand, ResponseEntity<ResourceDTO>> {

    private static final Logger logger =
            LoggerFactory.getLogger(AddItemsCommandHandler.class);

   private final ResourceRepository resourceRepository;
   private final PermissionRepository permissionRepository;
   private final ResourceMapper resourceMapper;

    /**
     * Constructs the {@code AddItemsCommandHandler} with required dependencies.
     *
     * @param resourceRepository     the repository used to fetch and persist {@link Resource} entities
     * @param resourceMapper         the mapper used to convert DTOs and domain models
     * @param permissionRepository   the repository used to fetch {@link Permission} entities
     */
   public AddItemsCommandHandler(
           ResourceRepository resourceRepository,
           ResourceMapper resourceMapper,
           PermissionRepository permissionRepository) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
      this.permissionRepository = permissionRepository;
   }

    /**
     * Handles the {@link AddItemsCommand} by appending mapped {@link ResourceItem}s to a {@link Resource}.
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
       logger.info("Handling AddItemsCommand for resource ID: {}", command.getId());

       Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {logger.warn("Resource not found with id: {}", command.getId());
                 return new IgrpResponseStatusException(
                         new IgrpProblem<>(HttpStatus.NOT_FOUND,
                                 "Resource not found",
                                 "Resource not found with id: " + command.getId()));
              });
      List<ResourceItem> items = command.getResourceitemdto().stream()
              .map(dto -> {
                  Permission permission = permissionRepository.findById(dto.getPermissionId())
                          .orElseThrow(() -> {
                              logger.warn("Permission not found with id: {}", dto.getPermissionId());
                              return new IgrpResponseStatusException(
                                      new IgrpProblem<>(HttpStatus.NOT_FOUND,
                                              "Permission not found",
                                              "Permission not found with id: " + dto.getPermissionId()));
                          });
                  return resourceMapper.toItemEntity(dto, resource, permission);
              })
              .toList();

       if (resource.getItems() == null) {
           logger.warn("Resource with id={} has null item list. Initializing new list.", resource.getId());
           resource.setItems(new ArrayList<>());
       }

       resource.getItems().addAll(items);
       var resourceSaved = resourceRepository.save(resource);

       logger.info("Added {} item(s) to resource id={}", items.size(), resource.getId());

       return ResponseEntity.ok(resourceMapper.toDto(resourceSaved));
   }

}
