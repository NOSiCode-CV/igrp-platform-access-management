package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.RemoveApplicationCustomFieldsCommand;

import java.util.Map;

/**
 * Command handler responsible for removing custom fields from an {@link Application} entity.
 * <p>
 * This handler processes a {@link RemoveApplicationCustomFieldsCommand}, identifies the corresponding
 * {@link CustomField} record for the application, removes the specified fields from its internal map,
 * and persists the updated state.
 * </p>
 *
 * <p>
 * If no {@link CustomField} entry is found for the given application ID, an {@link IgrpResponseStatusException}
 * is thrown with HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see RemoveApplicationCustomFieldsCommand
 * @see CustomField
 * @see CustomFieldRepository
 */
@Service
public class RemoveApplicationCustomFieldsCommandHandler implements CommandHandler<RemoveApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldRepository customFieldRepository;

   /**
    * Constructs a new instance of {@code RemoveApplicationCustomFieldsCommandHandler} with required dependencies.
    *
    * @param customFieldRepository repository used to retrieve and persist custom fields
    */
   public RemoveApplicationCustomFieldsCommandHandler(CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   /**
    * Handles the command to remove specific custom fields from an application.
    * <ul>
    *     <li>Retrieves the {@link CustomField} by table name and application ID.</li>
    *     <li>Removes each key present in the command from the field map.</li>
    *     <li>Saves the updated custom field back to the repository.</li>
    * </ul>
    *
    * @param command the command containing the application ID and keys to remove
    * @return a {@link ResponseEntity} with status {@code 204 NO_CONTENT} upon successful operation
    * @throws IgrpResponseStatusException if the custom field record does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveApplicationCustomFieldsCommand command) {
      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), command.getId())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "CustomField not found", "CustomField not found for Application ID: " + command.getId()));
      Map<String, Object> fields = customField.getFields();
      if (fields != null)
         command.getRemoveApplicationCustomFieldsRequest().forEach(fields::remove);
      customField.setFields(fields);
      customFieldRepository.save(customField);
      return ResponseEntity.noContent().build();
   }

}