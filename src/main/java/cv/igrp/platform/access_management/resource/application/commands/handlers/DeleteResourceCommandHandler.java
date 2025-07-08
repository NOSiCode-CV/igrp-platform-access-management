package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.DeleteResourceCommand;

import java.util.Optional;

/**
 * {@code DeleteResourceCommandHandler} is responsible for handling the deletion of a
 * {@link Resource} entity identified by its ID. If the resource exists, it will be removed from
 * the database. Additionally, any associated {@link CustomField} records will also be removed.
 *
 * <p>This handler ensures consistency between the resource and its related metadata.
 * In case the resource is not found, a {@link IgrpResponseStatusException} is thrown
 *
 * <p>Logging is provided for all major operations (resource lookup, deletion,
 * and custom field cleanup).
 *
 */
@Service
public class DeleteResourceCommandHandler implements
        CommandHandler<DeleteResourceCommand, ResponseEntity<String>> {

   private static final Logger logger =
           LoggerFactory.getLogger(DeleteResourceCommandHandler.class);

   private final ResourceRepository resourceRepository;
   private final CustomFieldRepository customFieldRepository;

   /**
    * Constructs the {@code DeleteResourceCommandHandler} with the required repositories.
    *
    * @param resourceRepository      the repository used to access and delete {@link Resource} entities
    * @param customFieldRepository   the repository used to access and delete {@link CustomField} entries
    */
   public DeleteResourceCommandHandler(
           ResourceRepository resourceRepository,
           CustomFieldRepository customFieldRepository) {
      this.resourceRepository = resourceRepository;
      this.customFieldRepository = customFieldRepository;
   }

   /**
    * Handles the deletion of a resource based on the provided {@link DeleteResourceCommand}.
    *
    * <p>If the resource exists, it is deleted from the database. If a custom field entry exists
    * for the same resource (using {@link CustomFieldTableName#RESOURCE}), it is also deleted.
    *
    * @param command the command containing the resource ID to be deleted
    * @return a {@link ResponseEntity} with HTTP 204 No Content if deletion was successful
    * @throws IgrpResponseStatusException if the resource with the specified ID does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteResourceCommand command) {
      Integer resourceId = command.getId();

      logger.info("Attempting to delete resource with ID: {}", resourceId);

      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 logger.warn("Resource not found with ID: {}", resourceId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with id: " + resourceId);
              });
      resourceRepository.delete(resource);
      logger.info("Deleted resource with ID: {}", resourceId);

      Optional<CustomField> customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId);

      customField.ifPresent(field -> {
         customFieldRepository.delete(field);
         logger.info("Deleted associated custom field for resource ID: {}", resourceId);
      });

      return ResponseEntity.noContent().build();
   }
}