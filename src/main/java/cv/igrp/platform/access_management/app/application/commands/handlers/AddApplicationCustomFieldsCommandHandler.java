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


@Service
public class AddApplicationCustomFieldsCommandHandler implements CommandHandler<AddApplicationCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldRepository customFieldRepository;
   private ApplicationRepository applicationRepository;

   public AddApplicationCustomFieldsCommandHandler(CustomFieldRepository customFieldRepository, ApplicationRepository applicationRepository) {
      this.customFieldRepository = customFieldRepository;
      this.applicationRepository = applicationRepository;
   }

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