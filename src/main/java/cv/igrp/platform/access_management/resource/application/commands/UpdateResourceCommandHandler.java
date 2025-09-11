package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

/**
 * Command handler responsible for processing the update of an existing {@link ResourceEntity} entity.
 * <p>
 * This handler locates the resource by its ID, updates its name and type fields based on the data
 * received in the {@link UpdateResourceCommand}, and then persists the changes.
 * </p>
 *
 * @see ResourceEntityRepository
 * @see ResourceMapper
 * @see UpdateResourceCommand
 * @see ResourceDTO
 */
@Component
public class UpdateResourceCommandHandler implements CommandHandler<UpdateResourceCommand, ResponseEntity<ResourceDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(UpdateResourceCommandHandler.class);

   private final ResourceEntityRepository resourceRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a new {@code UpdateResourceCommandHandler} with the required dependencies.
    *
    * @param resourceRepository the repository used to retrieve and persist {@link ResourceEntity} entities
    * @param resourceMapper the mapper used to convert between {@link ResourceEntity} and {@link ResourceDTO}
    */
   public UpdateResourceCommandHandler(ResourceEntityRepository resourceRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.resourceMapper = resourceMapper;
   }

   /**
    * Handles the update of a resource by applying the values from the {@link cv.igrp.platform.access_management.resource.application.commands.UpdateResourceCommand}.
    * <p>
    * If the resource is not found by the given ID, an {@link IgrpResponseStatusException} is thrown
    * with a 404 status code.
    * </p>
    * <p>
    * The updated entity is saved and returned as a DTO wrapped in a {@link ResponseEntity}.
    * </p>
    *
    * @param command the command containing the resource ID and the new values for update
    * @return a {@link ResponseEntity} containing the updated {@link ResourceDTO}
    * @throws IgrpResponseStatusException if the resource with the given ID does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(UpdateResourceCommand command) {
      String resourceName = command.getName();

      logger.info("Updating resource with name: {}", resourceName);

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with name: " + resourceName);
              });

      ResourceDTO dto = command.getResourcedto();

      resource.setName(dto.getName());
      resource.setType(dto.getType());

      var resourceUpdated = resourceRepository.save(resource);
      logger.info("Resource with name {} successfully updated.", resourceName);

      return ResponseEntity.ok(resourceMapper.toDto(resourceUpdated));
   }

}