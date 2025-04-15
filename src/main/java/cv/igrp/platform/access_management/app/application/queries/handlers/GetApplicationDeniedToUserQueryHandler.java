package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationDeniedToUserQuery;

import java.util.ArrayList;
import java.util.List;

@Service
public class GetApplicationDeniedToUserQueryHandler implements QueryHandler<GetApplicationDeniedToUserQuery, ResponseEntity<List<ApplicationDTO>>>{

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public GetApplicationDeniedToUserQueryHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationDeniedToUserQuery query) {
      List<ApplicationDTO> applications = new ArrayList<>();
      return ResponseEntity.ok(applications);
   }

}