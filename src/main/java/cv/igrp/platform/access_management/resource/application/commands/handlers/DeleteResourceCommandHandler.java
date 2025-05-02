package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.DeleteResourceCommand;

import java.util.Optional;


@Service
public class DeleteResourceCommandHandler implements CommandHandler<DeleteResourceCommand, ResponseEntity<String>> {

   private ResourceRepository resourceRepository;
   private CustomFieldRepository customFieldRepository;

   public DeleteResourceCommandHandler(ResourceRepository resourceRepository, CustomFieldRepository customFieldRepository) {
      this.resourceRepository = resourceRepository;
      this.customFieldRepository = customFieldRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteResourceCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + command.getId()));
              });
      Optional<CustomField> customField = customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), command.getId());
      resourceRepository.delete(resource);
      customField.ifPresent(field -> customFieldRepository.delete(field));
      return ResponseEntity.noContent().build();
   }

}