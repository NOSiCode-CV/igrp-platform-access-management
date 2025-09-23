package cv.igrp.platform.access_management.department.application.queries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

@Component
public class GetAvailableResourcesForDepartmentQueryHandler implements QueryHandler<GetAvailableResourcesForDepartmentQuery, ResponseEntity<List<ResourceDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailableResourcesForDepartmentQueryHandler.class);


  public GetAvailableResourcesForDepartmentQueryHandler() {

  }

   @IgrpQueryHandler
  public ResponseEntity<List<ResourceDTO>> handle(GetAvailableResourcesForDepartmentQuery query) {
    // TODO: Implement the query handling logic here
    return null;
  }

}