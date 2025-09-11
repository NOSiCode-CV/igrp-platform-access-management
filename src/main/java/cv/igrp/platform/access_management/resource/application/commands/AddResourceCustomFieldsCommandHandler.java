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
import java.util.Map;
import java.util.Optional;


/**
 * {@code AddResourceCustomFieldsCommandHandler} is responsible for handling the command
 * to add or update custom fields associated with a specific {@link ResourceEntity}.
 * <p>
 * This command handler ensures that a {@link CustomFieldEntity} entity exists for the given resource.
 * If it does not exist, the handler creates and initializes it with an empty field map. Then, it updates
 * or adds the key-value pairs provided in the {@link AddResourceCustomFieldsCommand} to the custom field entity.
 * The result is persisted using the {@link CustomFieldEntityRepository}.
 * </p>
 * <p>
 * Custom fields are stored in a generic map structure and associated via a combination of table name
 * (in this case, {@code RESOURCE}) and record ID (the resource ID).
 * </p>
 *
 * <p><b>Expected behavior:</b></p>
 * <ul>
 *   <li>If the resource ID is invalid, throws {@link IgrpResponseStatusException} with 404.</li>
 *   <li>If the {@code CustomFieldEntity} for the resource does not exist, a new one is created.</li>
 *   <li>The new fields provided in the command are merged with any existing fields.</li>
 * </ul>
 *
 * @see AddResourceCustomFieldsCommand
 * @see CustomFieldEntityRepository
 * @see ResourceEntityRepository
 * @see CustomFieldEntity
 * @see ResourceEntity
 */
@Component
public class AddResourceCustomFieldsCommandHandler implements CommandHandler<AddResourceCustomFieldsCommand, ResponseEntity<String>> {

   private static final Logger logger =
           LoggerFactory.getLogger(AddResourceCustomFieldsCommandHandler.class);

   private final CustomFieldEntityRepository customFieldRepository;
   private final ResourceEntityRepository resourceRepository;

   /**
    * Constructs a new {@code AddResourceCustomFieldsCommandHandler} with the required dependencies.
    *
    * @param customFieldRepository the repository used to retrieve and persist {@link CustomFieldEntity} entities
    * @param resourceRepository the repository used to retrieve {@link ResourceEntity} entities by ID
    */
   public AddResourceCustomFieldsCommandHandler(
           CustomFieldEntityRepository customFieldRepository,
           ResourceEntityRepository resourceRepository) {
      this.customFieldRepository = customFieldRepository;
      this.resourceRepository = resourceRepository;
   }

   /**
    * Handles the {@link AddResourceCustomFieldsCommand} by updating or initializing a {@link CustomFieldEntity}
    * record linked to the given {@link ResourceEntity} ID and storing the provided custom field values.
    *
    * <p>If no {@code CustomFieldEntity} is found for the given resource, a new one is created.
    * All provided fields are merged with existing values (if any).</p>
    *
    * @param command the command object containing the resource ID and custom field values to add
    * @return a {@link ResponseEntity} with HTTP 204 No Content on successful processing
    * @throws IgrpResponseStatusException if the resource does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddResourceCustomFieldsCommand command) {

      String resourceName = command.getName();

      logger.info("Handling AddResourceCustomFieldsCommand for resource name: {}", resourceName);

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found for name: " + resourceName);
              });

      CustomFieldEntity customField = customFieldRepository.findByTableNameAndRecordId(
                      CustomFieldTableName.RESOURCE.getName(), resource.getId())
              .orElseGet(() -> {
                 logger.info("No custom field found for resource name: {}. " +
                         "Creating new CustomFieldEntity entry.", resourceName);
                 CustomFieldEntity cf = new CustomFieldEntity();
                 cf.setTableName(CustomFieldTableName.RESOURCE.getName());
                 cf.setRecordId(resource.getId());
                 cf.setFields(new HashMap<>());
                 return cf;
              });

      Map<String, Object> existingFields = Optional.ofNullable(customField.getFields())
              .orElse(new HashMap<>());
      existingFields.putAll(command.getAddResourceCustomFieldsRequest());
      customField.setFields(existingFields);

      customFieldRepository.save(customField);
      logger.info("Successfully updated custom fields for resource name: {}", resourceName);

      return ResponseEntity.noContent().build();
   }

}