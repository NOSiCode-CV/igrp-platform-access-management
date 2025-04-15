package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.UpdateApplicationCommand;



@Service
public class UpdateApplicationCommandHandler implements CommandHandler<UpdateApplicationCommand, ResponseEntity<ApplicationDTO>> {

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public UpdateApplicationCommandHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ApplicationDTO> handle(UpdateApplicationCommand command) {
      Application application = applicationRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Application not found with id: " + command.getId()));
      application.setCode(command.getApplicationdto().getCode());
      application.setName(command.getApplicationdto().getName());
      application.setDescription(command.getApplicationdto().getDescription());
      application.setStatus(command.getApplicationdto().getStatus());
      application.setType(command.getApplicationdto().getType());
      application.setOwner(command.getApplicationdto().getOwner());
      application.setPicture(command.getApplicationdto().getPicture());
      application.setUrl(command.getApplicationdto().getUrl() != null ? command.getApplicationdto().getUrl().toString() : null);
      application.setSlug(command.getApplicationdto().getSlug());
      Application updatedApplication = applicationRepository.save(application);
      return ResponseEntity.ok(applicationMapper.toDto(updatedApplication));
   }

}