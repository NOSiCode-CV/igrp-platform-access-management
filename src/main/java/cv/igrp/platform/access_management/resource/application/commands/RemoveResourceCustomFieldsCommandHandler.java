package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Command handler responsible for removing custom fields associated with a {@link ResourceEntity}.
 * <p>
 * This handler processes a {@link RemoveResourceCustomFieldsCommand}, retrieves the corresponding
 * {@link CustomFieldEntity} entity from the database, removes specified keys from its map of fields,
 * and persists the updated entity.
 * </p>
 *
 * <p>
 * If the {@link CustomFieldEntity} is not found for the given resource ID, an {@link IgrpResponseStatusException}
 * with HTTP 404 is thrown.
 * </p>
 *
 * @see CommandHandler
 * @see CustomFieldEntity
 * @see CustomFieldEntityRepository
 * @see RemoveResourceCustomFieldsCommand
 */
@Component
public class RemoveResourceCustomFieldsCommandHandler implements CommandHandler<RemoveResourceCustomFieldsCommand, ResponseEntity<String>> {

   private static final Logger logger =
           LoggerFactory.getLogger(RemoveResourceCustomFieldsCommandHandler.class);

   private final CustomFieldEntityRepository customFieldRepository;
   private final ResourceEntityRepository resourceRepository;

   /**
    * Constructs a new instance of {@code RemoveResourceCustomFieldsCommandHandler}.
    *
    * @param customFieldRepository the repository used to retrieve and update custom field entries
    */
   public RemoveResourceCustomFieldsCommandHandler(
           CustomFieldEntityRepository customFieldRepository, ResourceEntityRepository resourceRepository) {
      this.customFieldRepository = customFieldRepository;
      this.resourceRepository = resourceRepository;
   }

   /**
    * Handles the removal of custom fields for a given {@link ResourceEntity} entity based on its ID.
    * <p>
    * The method checks if the resource's {@link CustomFieldEntity} exists; if not, an exception is thrown.
    * Then it removes the specified field keys from the custom field map and saves the changes.
    * </p>
    *
    * @param command the command containing the resource ID and the list of custom field keys to remove
    * @return a {@link ResponseEntity} with HTTP 204 No Content status upon successful update
    * @throws IgrpResponseStatusException if the custom field entity for the specified resource ID does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveResourceCustomFieldsCommand command) {
      String resourceName = command.getName();

      logger.info("Processing removal of custom fields for resource name: {}", resourceName);

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with name: " + resourceName);
              });

      CustomFieldEntity customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resource.getId())
              .orElseThrow(() -> {
                 logger.warn("CustomFieldEntity not found for resource name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "CustomFieldEntity not found",
                         "CustomFieldEntity not found for Resource name: " + resourceName);
              });

      Map<String, Object> fields = Optional.ofNullable(customField.getFields()).orElse(new HashMap<>());
      List<String> keysToRemove = Optional.ofNullable(command.getRemoveResourceCustomFieldsRequest()).orElse(List.of());

      logger.info("Removing {} custom field(s) from resource name: {}", keysToRemove.size(), resourceName);

      keysToRemove.forEach(fields::remove);

      customField.setFields(fields);
      customFieldRepository.save(customField);

      logger.info("Successfully removed custom fields from resource name: {}", resourceName);
      return ResponseEntity.noContent().build();
   }

}