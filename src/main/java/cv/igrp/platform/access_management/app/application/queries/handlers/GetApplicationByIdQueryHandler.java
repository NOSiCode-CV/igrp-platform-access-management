package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationByIdQuery;


@Service
public class GetApplicationByIdQueryHandler implements QueryHandler<GetApplicationByIdQuery, ResponseEntity<ApplicationDTO>>{

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public GetApplicationByIdQueryHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<ApplicationDTO> handle(GetApplicationByIdQuery query) {
      Application application = applicationRepository.findById(query.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + query.getId()));
              });
      return ResponseEntity.ok(applicationMapper.toDto(application));
   }

}