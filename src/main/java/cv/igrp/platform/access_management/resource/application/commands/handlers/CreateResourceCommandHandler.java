package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.CreateResourceCommand;

import java.util.List;


@Service
public class CreateResourceCommandHandler implements CommandHandler<CreateResourceCommand, ResponseEntity<ResourceDTO>> {

   private ResourceRepository resourceRepository;
   private ApplicationRepository applicationRepository;
   private ResourceMapper resourceMapper;

   public CreateResourceCommandHandler(ResourceRepository resourceRepository, ApplicationRepository applicationRepository, ResourceMapper resourceMapper) {
      this.resourceRepository = resourceRepository;
      this.applicationRepository = applicationRepository;
      this.resourceMapper = resourceMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(CreateResourceCommand command) {
      Resource resource = resourceMapper.toEntity(command.getResourcedto());
      resource.setStatus(Status.ACTIVE);

      Integer applicationId = command.getResourcedto().getApplicationId();
      Application application = applicationRepository.findById(applicationId)
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " +applicationId));
              });
      resource.setApplicationId(application);

      if (command.getResourcedto().getItems() != null && !command.getResourcedto().getItems().isEmpty()) {
         List<ResourceItem> items = command.getResourcedto().getItems().stream()
                 .map(itemDTO -> {
                     return resourceMapper.toItemEntity(itemDTO, resource);
                 }).toList();
         resource.setItems(items);
      }

      return ResponseEntity.status(HttpStatus.CREATED).body(resourceMapper.toDto(resourceRepository.save(resource)));
   }

}