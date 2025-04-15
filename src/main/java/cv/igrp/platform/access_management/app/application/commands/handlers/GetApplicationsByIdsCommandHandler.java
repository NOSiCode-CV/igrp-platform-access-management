package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.GetApplicationsByIdsCommand;

import java.util.List;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Service
public class GetApplicationsByIdsCommandHandler implements CommandHandler<GetApplicationsByIdsCommand, ResponseEntity<List<ApplicationDTO>>> {

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public GetApplicationsByIdsCommandHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsByIdsCommand query) {
      List<Application> applications = applicationRepository.findAllById(query.getGetApplicationsByIdsRequest());
      List<ApplicationDTO> applicationDTOs = applications.stream()
              .map(applicationMapper::toDto)
              .toList();
      return ResponseEntity.ok(applicationDTOs);
   }

}