package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.CreateApplicationQuery;


@Service
public class CreateApplicationQueryHandler implements QueryHandler<CreateApplicationQuery, ResponseEntity<?>>{

   public CreateApplicationQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<?> handle(CreateApplicationQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}