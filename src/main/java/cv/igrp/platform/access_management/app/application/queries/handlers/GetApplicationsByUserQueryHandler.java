package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationsByUserQuery;

import java.util.ArrayList;
import java.util.List;

@Service
public class GetApplicationsByUserQueryHandler implements QueryHandler<GetApplicationsByUserQuery, ResponseEntity<List<ApplicationDTO>>>{

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public GetApplicationsByUserQueryHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsByUserQuery query) {
      List<Application> applications = applicationRepository
              .findDistinctByDepartmentses_Roleses_Users_UsernameOrDepartmentses_Roleses_Users_Email(query.getUid(), query.getUid());

      List<ApplicationDTO> applicationDTOs = applications.stream()
              .map(applicationMapper::toDto)
              .toList();

      return ResponseEntity.ok(applicationDTOs);
   }

}