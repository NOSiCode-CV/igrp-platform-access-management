package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.RemoveItemsCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the removal of specific items from a {@link Resource}.
 * <p>
 * This command handler is responsible for processing a {@link RemoveItemsCommand}
 * by removing the specified resource item IDs from a resource and returning the updated
 * {@link ResourceDTO}. If the list of item IDs is {@code null} or empty, no changes are made
 * and the current state of the resource is returned.
 * </p>
 *
 * @see RemoveItemsCommand
 * @see ResourceRepository
 * @see ResourceMapper
 */
@Service
public class RemoveItemsCommandHandler implements
        CommandHandler<RemoveItemsCommand, ResponseEntity<ResourceDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(RemoveItemsCommandHandler.class);

   private final ResourceRepository resourceRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a {@code RemoveItemsCommandHandler} with required dependencies.
    *
    * @param resourceRepository the repository for retrieving and saving resources
    * @param resourceMapper     the mapper for converting {@link Resource} to {@link ResourceDTO}
    */
   public RemoveItemsCommandHandler(
           ResourceRepository resourceRepository,
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
      Integer resourceId = command.getId();

      logger.info("Handling RemoveItemsCommand for resource ID: {}", resourceId);

      Resource resource = resourceRepository.findById(resourceId)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with ID: {}", resourceId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with id: " + resourceId);
              });

      List<Integer> itemsToRemove = command.getRemoveItemsRequest();
      if (itemsToRemove == null || itemsToRemove.isEmpty()) {
         logger.info("No item IDs provided for removal; skipping item removal.");
         return ResponseEntity.ok(resourceMapper.toDto(resource));
      }

      List<ResourceItem> items = Optional.ofNullable(resource.getItems()).orElse(new ArrayList<>());

      int beforeRemove = itemsToRemove.size();
      items.removeIf(item -> itemsToRemove.contains(item.getId()));
      int afterRemove = beforeRemove - itemsToRemove.size();

      logger.info("Removed {} item(s) from resource ID: {}", afterRemove, resource.getId());

      Resource saved = resourceRepository.save(resource);

      return ResponseEntity.ok(resourceMapper.toDto(saved));
   }

}