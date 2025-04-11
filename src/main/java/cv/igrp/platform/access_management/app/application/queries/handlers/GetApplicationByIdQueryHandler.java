package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationByIdQuery;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Service
public class GetApplicationByIdQueryHandler implements QueryHandler<GetApplicationByIdQuery, ResponseEntity<ApplicationDTO>>{

   public GetApplicationByIdQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<ApplicationDTO> handle(GetApplicationByIdQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}