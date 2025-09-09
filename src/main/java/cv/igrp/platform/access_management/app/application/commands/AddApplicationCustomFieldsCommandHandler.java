package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Command handler responsible for adding or updating custom fields for a specific {@link ApplicationEntity}.
 *
 * <p>
 * This handler:
 * <ul>
 *   <li>Retrieves the application by its ID, throwing an exception if not found.</li>
 *   <li>Fetches or creates a {@link CustomFieldEntity} associated with the application.</li>
 *   <li>Merges the incoming fields into the existing custom fields map.</li>
 *   <li>Persists the updated custom field entity.</li>
 * </ul>
 *
 * <p>
 * The operation completes with a {@link ResponseEntity} containing no content (204).
 * </p>
 *
 * @see AddApplicationCustomFieldsCommand
 * @see ApplicationEntity
 * @see CustomFieldEntity
 * @see CustomFieldEntityRepository
 * @see ApplicationEntityRepository
 * @see IgrpResponseStatusException
 */
@Component
public class AddApplicationCustomFieldsCommandHandler implements CommandHandler<AddApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private final CustomFieldEntityRepository customFieldRepository;
   private final ApplicationEntityRepository applicationRepository;

   /**
    * Constructs the handler with the required repositories.
    *
    * @param customFieldRepository the repository used to manage {@link CustomFieldEntity} entities
    * @param applicationRepository the repository used to retrieve {@link ApplicationEntity} entities
    */
   public AddApplicationCustomFieldsCommandHandler(CustomFieldEntityRepository customFieldRepository, ApplicationEntityRepository applicationRepository) {
      this.customFieldRepository = customFieldRepository;
      this.applicationRepository = applicationRepository;
   }

   /**
    * Handles the command to add or update custom fields for an application.
    *
    * @param command the {@link AddApplicationCustomFieldsCommand} containing the application ID and the fields to update
    * @return a {@link ResponseEntity} with HTTP 204 No Content on success
    * @throws IgrpResponseStatusException if the application is not found
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddApplicationCustomFieldsCommand command) {
      ApplicationEntity application = applicationRepository.findByCode(command.getCode())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found for code: " + command.getCode()));

      CustomFieldEntity customField = customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), application.getId())
              .orElseGet(() -> {
                 CustomFieldEntity cf = new CustomFieldEntity();
                 cf.setTableName(CustomFieldTableName.APPLICATION.getName());
                 cf.setRecordId(application.getId());
                 cf.setFields(new HashMap<>());
                 return cf;
              });

      Map<String, Object> fields = customField.getFields();
      fields.putAll(command.getAddApplicationCustomFieldsRequest());
      customField.setFields(fields);

      customFieldRepository.save(customField);

      return ResponseEntity.noContent().build();
   }

}