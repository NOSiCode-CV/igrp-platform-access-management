package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + command.getId()));
              });

      ApplicationDTO appDto = command.getApplicationdto();
      application.setCode(appDto.getCode());
      application.setName(appDto.getName());
      application.setDescription(appDto.getDescription());
      application.setStatus(appDto.getStatus());
      application.setType(appDto.getType());
      application.setOwner(appDto.getOwner());
      application.setPicture(appDto.getPicture());
      application.setUrl(appDto.getUrl() != null ? appDto.getUrl().toString() : null);
      application.setSlug(appDto.getSlug());

      Application updatedApplication = applicationRepository.save(application);
      return ResponseEntity.ok(applicationMapper.toDto(updatedApplication));
   }

}