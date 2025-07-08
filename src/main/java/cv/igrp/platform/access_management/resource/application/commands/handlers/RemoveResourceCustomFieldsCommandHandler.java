package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.RemoveResourceCustomFieldsCommand;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Command handler responsible for removing custom fields associated with a {@link Resource}.
 * <p>
 * This handler processes a {@link RemoveResourceCustomFieldsCommand}, retrieves the corresponding
 * {@link CustomField} entity from the database, removes specified keys from its map of fields,
 * and persists the updated entity.
 * </p>
 *
 * <p>
 * If the {@link CustomField} is not found for the given resource ID, an {@link IgrpResponseStatusException}
 * with HTTP 404 is thrown.
 * </p>
 *
 * @see CommandHandler
 * @see CustomField
 * @see CustomFieldRepository
 * @see RemoveResourceCustomFieldsCommand
 */
@Service
public class RemoveResourceCustomFieldsCommandHandler implements
        CommandHandler<RemoveResourceCustomFieldsCommand, ResponseEntity<String>> {

   private static final Logger logger =
           LoggerFactory.getLogger(RemoveResourceCustomFieldsCommandHandler.class);

   private final CustomFieldRepository customFieldRepository;

   /**
    * Constructs a new instance of {@code RemoveResourceCustomFieldsCommandHandler}.
    *
    * @param customFieldRepository the repository used to retrieve and update custom field entries
    */
   public RemoveResourceCustomFieldsCommandHandler(
           CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   /**
    * Handles the removal of custom fields for a given {@link Resource} entity based on its ID.
    * <p>
    * The method checks if the resource's {@link CustomField} exists; if not, an exception is thrown.
    * Then it removes the specified field keys from the custom field map and saves the changes.
    * </p>
    *
    * @param command the command containing the resource ID and the list of custom field keys to remove
    * @return a {@link ResponseEntity} with HTTP 204 No Content status upon successful update
    * @throws IgrpResponseStatusException if the custom field entity for the specified resource ID does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveResourceCustomFieldsCommand command) {
      Integer resourceId = command.getId();

      logger.info("Processing removal of custom fields for resource ID: {}", resourceId);

      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId)
              .orElseThrow(() -> {
                 logger.warn("CustomField not found for resource ID: {}", resourceId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "CustomField not found",
                         "CustomField not found for Resource ID: " + resourceId);
              });

      Map<String, Object> fields = Optional.ofNullable(customField.getFields()).orElse(new HashMap<>());
      List<String> keysToRemove = Optional.ofNullable(command.getRemoveResourceCustomFieldsRequest()).orElse(List.of());

      logger.info("Removing {} custom field(s) from resource ID: {}", keysToRemove.size(), resourceId);

      keysToRemove.forEach(fields::remove);

      customField.setFields(fields);
      customFieldRepository.save(customField);

      logger.info("Successfully removed custom fields from resource ID: {}", resourceId);
      return ResponseEntity.noContent().build();
   }

}