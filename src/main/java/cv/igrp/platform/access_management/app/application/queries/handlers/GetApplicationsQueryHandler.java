package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationsQuery;


@Service
public class GetApplicationsQueryHandler implements QueryHandler<GetApplicationsQuery, ResponseEntity<ApplicationDTO>>{

   public GetApplicationsQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<ApplicationDTO> handle(GetApplicationsQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}