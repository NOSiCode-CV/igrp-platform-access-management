package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.RemoveApplicationCustomFieldsCommand;

import java.util.Map;


@Service
public class RemoveApplicationCustomFieldsCommandHandler implements CommandHandler<RemoveApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldRepository customFieldRepository;

   public RemoveApplicationCustomFieldsCommandHandler(CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveApplicationCustomFieldsCommand command) {
      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "CustomField not found", "CustomField not found for Application ID: " + command.getId()));
              });
      Map<String, Object> fields = customField.getFields();
      if (fields != null)
         command.getRemoveApplicationCustomFieldsRequest().forEach(fields::remove);
      customField.setFields(fields);
      customFieldRepository.save(customField);
      return ResponseEntity.noContent().build();
   }

}