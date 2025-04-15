package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.CreateApplicationCommand;



@Service
public class CreateApplicationCommandHandler implements CommandHandler<CreateApplicationCommand, ResponseEntity<ApplicationDTO>> {

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public CreateApplicationCommandHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<ApplicationDTO> handle(CreateApplicationCommand command) {
      Application application = applicationMapper.toEntity(command.getApplicationdto());
      application.setId(null);
      application.setStatus(Status.ACTIVE);
      Application savedApplication = applicationRepository.save(application);
      ApplicationDTO applicationDTO =  applicationMapper.toDto(savedApplication);
      return ResponseEntity.status(HttpStatus.CREATED).body(applicationDTO);
   }

}