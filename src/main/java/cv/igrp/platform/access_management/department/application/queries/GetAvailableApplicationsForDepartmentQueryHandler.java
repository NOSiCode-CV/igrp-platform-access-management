package cv.igrp.platform.access_management.department.application.queries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Component
public class GetAvailableApplicationsForDepartmentQueryHandler implements QueryHandler<GetAvailableApplicationsForDepartmentQuery, ResponseEntity<List<ApplicationDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailableApplicationsForDepartmentQueryHandler.class);


  public GetAvailableApplicationsForDepartmentQueryHandler() {

  }

   @IgrpQueryHandler
  public ResponseEntity<List<ApplicationDTO>> handle(GetAvailableApplicationsForDepartmentQuery query) {
    // TODO: Implement the query handling logic here
    return null;
  }

}