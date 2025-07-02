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
import cv.igrp.platform.access_management.resource.application.commands.commands.AddResourceCustomFieldsCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code AddResourceCustomFieldsCommandHandler} is responsible for handling the command
 * to add or update custom fields associated with a specific {@link Resource}.
 * <p>
 * This command handler ensures that a {@link CustomField} entity exists for the given resource.
 * If it does not exist, the handler creates and initializes it with an empty field map. Then, it updates
 * or adds the key-value pairs provided in the {@link AddResourceCustomFieldsCommand} to the custom field entity.
 * The result is persisted using the {@link CustomFieldRepository}.
 * </p>
 * <p>
 * Custom fields are stored in a generic map structure and associated via a combination of table name
 * (in this case, {@code RESOURCE}) and record ID (the resource ID).
 * </p>
 *
 * <p><b>Expected behavior:</b></p>
 * <ul>
 *   <li>If the resource ID is invalid, throws {@link IgrpResponseStatusException} with 404.</li>
 *   <li>If the {@code CustomField} for the resource does not exist, a new one is created.</li>
 *   <li>The new fields provided in the command are merged with any existing fields.</li>
 * </ul>
 *
 * @see AddResourceCustomFieldsCommand
 * @see CustomFieldRepository
 * @see ResourceRepository
 * @see CustomField
 * @see Resource
 */
@Service
public class AddResourceCustomFieldsCommandHandler implements
        CommandHandler<AddResourceCustomFieldsCommand, ResponseEntity<String>> {

    private static final Logger logger =
            LoggerFactory.getLogger(AddResourceCustomFieldsCommandHandler.class);

   private final CustomFieldRepository customFieldRepository;
   private final ResourceRepository resourceRepository;

    /**
     * Constructs a new {@code AddResourceCustomFieldsCommandHandler} with the required dependencies.
     *
     * @param customFieldRepository the repository used to retrieve and persist {@link CustomField} entities
     * @param resourceRepository the repository used to retrieve {@link Resource} entities by ID
     */
   public AddResourceCustomFieldsCommandHandler(
           CustomFieldRepository customFieldRepository,
           ResourceRepository resourceRepository) {
      this.customFieldRepository = customFieldRepository;
      this.resourceRepository = resourceRepository;
   }

    /**
     * Handles the {@link AddResourceCustomFieldsCommand} by updating or initializing a {@link CustomField}
     * record linked to the given {@link Resource} ID and storing the provided custom field values.
     *
     * <p>If no {@code CustomField} is found for the given resource, a new one is created.
     * All provided fields are merged with existing values (if any).</p>
     *
     * @param command the command object containing the resource ID and custom field values to add
     * @return a {@link ResponseEntity} with HTTP 204 No Content on successful processing
     * @throws IgrpResponseStatusException if the resource does not exist
     */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddResourceCustomFieldsCommand command) {
       Integer resourceId = command.getId();

       logger.info("Handling AddResourceCustomFieldsCommand for resource ID: {}", resourceId);

       Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                  logger.warn("Resource not found with ID: {}", resourceId);
                  return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                          "Resource not found",
                          "Resource not found for ID: " + resourceId);
              });

      CustomField customField = customFieldRepository.findByTableNameAndRecordId(
              CustomFieldTableName.RESOURCE.getName(), resource.getId())
              .orElseGet(() -> {
                  logger.info("No custom field found for resource ID: {}. " +
                          "Creating new CustomField entry.", resourceId);
                  CustomField cf = new CustomField();
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
      logger.info("Successfully updated custom fields for resource ID: {}", resourceId);

      return ResponseEntity.noContent().build();
   }

}