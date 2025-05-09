package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.AddApplicationCustomFieldsCommand;

import java.util.HashMap;
import java.util.Map;


/**
 * Command handler responsible for adding or updating custom fields for a specific {@link Application}.
 *
 * <p>
 * This handler:
 * <ul>
 *   <li>Retrieves the application by its ID, throwing an exception if not found.</li>
 *   <li>Fetches or creates a {@link CustomField} associated with the application.</li>
 *   <li>Merges the incoming fields into the existing custom fields map.</li>
 *   <li>Persists the updated custom field entity.</li>
 * </ul>
 *
 * <p>
 * The operation completes with a {@link ResponseEntity} containing no content (204).
 * </p>
 *
 * @see AddApplicationCustomFieldsCommand
 * @see Application
 * @see CustomField
 * @see CustomFieldRepository
 * @see ApplicationRepository
 * @see IgrpResponseStatusException
 */
@Service
public class AddApplicationCustomFieldsCommandHandler implements CommandHandler<AddApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldRepository customFieldRepository;
   private ApplicationRepository applicationRepository;

    /**
     * Constructs the handler with the required repositories.
     *
     * @param customFieldRepository the repository used to manage {@link CustomField} entities
     * @param applicationRepository the repository used to retrieve {@link Application} entities
     */
   public AddApplicationCustomFieldsCommandHandler(CustomFieldRepository customFieldRepository, ApplicationRepository applicationRepository) {
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
      Application application = applicationRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found for ID: " + command.getId()));
              });

      CustomField customField = customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), application.getId())
              .orElseGet(() -> {
                 CustomField cf = new CustomField();
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