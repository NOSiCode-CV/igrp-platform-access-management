package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.AddResourceCustomFieldsCommand;

import java.util.HashMap;
import java.util.Map;


@Service
public class AddResourceCustomFieldsCommandHandler implements CommandHandler<AddResourceCustomFieldsCommand, ResponseEntity<String>> {

   private CustomFieldRepository customFieldRepository;
   private ResourceRepository resourceRepository;

   public AddResourceCustomFieldsCommandHandler(CustomFieldRepository customFieldRepository, ResourceRepository resourceRepository) {
      this.customFieldRepository = customFieldRepository;
      this.resourceRepository = resourceRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddResourceCustomFieldsCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found for ID: " + command.getId()));
              });

      CustomField customField = customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resource.getId())
              .orElseGet(() -> {
                 CustomField cf = new CustomField();
                 cf.setTableName(CustomFieldTableName.RESOURCE.getName());
                 cf.setRecordId(resource.getId());
                 cf.setFields(new HashMap<>());
                 return cf;
              });

      Map<String, Object> fields = customField.getFields();
      fields.putAll(command.getAddResourceCustomFieldsRequest());
      customField.setFields(fields);

      customFieldRepository.save(customField);

      return ResponseEntity.noContent().build();
   }

}