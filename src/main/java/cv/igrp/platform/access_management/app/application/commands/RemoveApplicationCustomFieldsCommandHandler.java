package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;


/**
 * Command handler responsible for removing custom fields from an {@link ApplicationEntity} entity.
 * <p>
 * This handler processes a {@link RemoveApplicationCustomFieldsCommand}, identifies the corresponding
 * {@link CustomFieldEntity} record for the application, removes the specified fields from its internal map,
 * and persists the updated state.
 * </p>
 *
 * <p>
 * If no {@link CustomFieldEntity} entry is found for the given application ID, an {@link IgrpResponseStatusException}
 * is thrown with HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see RemoveApplicationCustomFieldsCommand
 * @see CustomFieldEntity
 * @see CustomFieldEntityRepository
 */
@Service
public class RemoveApplicationCustomFieldsCommandHandler implements CommandHandler<RemoveApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldEntityRepository customFieldRepository;
   private ApplicationEntityRepository applicationRepository;

   /**
    * Constructs a new instance of {@code RemoveApplicationCustomFieldsCommandHandler} with required dependencies.
    *
    * @param customFieldRepository repository used to retrieve and persist custom fields
    */
   public RemoveApplicationCustomFieldsCommandHandler(CustomFieldEntityRepository customFieldRepository, ApplicationEntityRepository applicationRepository) {
      this.customFieldRepository = customFieldRepository;
      this.applicationRepository = applicationRepository;
   }

   /**
    * Handles the command to remove specific custom fields from an application.
    * <ul>
    *     <li>Retrieves the {@link CustomFieldEntity} by table name and application ID.</li>
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
      ApplicationEntity application = applicationRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found with code: " + command.getCode()));
      CustomFieldEntity customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), application.getId())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "CustomFieldEntity not found", "CustomFieldEntity not found for Application ID: " + application.getId()));
      Map<String, Object> fields = customField.getFields();
      if (fields != null)
         command.getRemoveApplicationCustomFieldsRequest().forEach(fields::remove);
      customField.setFields(fields);
      customFieldRepository.save(customField);
      return ResponseEntity.noContent().build();
   }

}