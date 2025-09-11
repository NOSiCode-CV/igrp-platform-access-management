package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the removal of specific items from a {@link ResourceEntity}.
 * <p>
 * This command handler is responsible for processing a {@link RemoveItemsCommand}
 * by removing the specified resource item IDs from a resource and returning the updated
 * {@link ResourceDTO}. If the list of item IDs is {@code null} or empty, no changes are made
 * and the current state of the resource is returned.
 * </p>
 *
 * @see RemoveItemsCommand
 * @see ResourceEntityRepository
 * @see ResourceMapper
 */
@Component
public class RemoveItemsCommandHandler implements CommandHandler<RemoveItemsCommand, ResponseEntity<ResourceDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(RemoveItemsCommandHandler.class);

   private final ResourceEntityRepository resourceRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a {@code RemoveItemsCommandHandler} with required dependencies.
    *
    * @param resourceRepository the repository for retrieving and saving resources
    * @param resourceMapper     the mapper for converting {@link ResourceEntity} to {@link ResourceDTO}
    */
   public RemoveItemsCommandHandler(
           ResourceEntityRepository resourceRepository,
           ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   /**
    * Executes the {@link RemoveItemsCommand} by removing specified item IDs from the target resource.
    *
    * @param command the command containing the resource ID and the list of item IDs to remove
    * @return a {@link ResponseEntity} containing the updated {@link ResourceDTO}
    * @throws IgrpResponseStatusException if the specified resource does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(RemoveItemsCommand command) {
      String resourceName = command.getName();

      logger.info("Handling RemoveItemsCommand for resource name: {}", resourceName);

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with name: " + resourceName);
              });

      List<Integer> itemsToRemove = command.getRemoveItemsRequest();
      if (itemsToRemove == null || itemsToRemove.isEmpty()) {
         logger.info("No item IDs provided for removal; skipping item removal.");
         return ResponseEntity.ok(resourceMapper.toDto(resource));
      }

      List<ResourceItemEntity> items = Optional.ofNullable(resource.getItems()).orElse(new ArrayList<>());

      int beforeRemove = itemsToRemove.size();
      items.removeIf(item -> itemsToRemove.contains(item.getId()));
      int afterRemove = beforeRemove - itemsToRemove.size();

      logger.info("Removed {} item(s) from resource ID: {}", afterRemove, resource.getId());

      ResourceEntity saved = resourceRepository.save(resource);

      return ResponseEntity.ok(resourceMapper.toDto(saved));
   }

}