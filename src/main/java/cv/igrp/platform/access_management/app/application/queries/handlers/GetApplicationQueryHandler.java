package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationQuery;
import java.util.Collection;

@Service
public class GetApplicationQueryHandler implements QueryHandler<GetApplicationQuery, ResponseEntity<Collection<Application>>>{

   public GetApplicationQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<Collection<Application>> handle(GetApplicationQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}