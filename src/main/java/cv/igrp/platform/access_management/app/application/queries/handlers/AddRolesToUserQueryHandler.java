package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.AddRolesToUserQuery;
import java.util.List;

@Service
public class AddRolesToUserQueryHandler implements QueryHandler<AddRolesToUserQuery, ResponseEntity<?>>{

   public AddRolesToUserQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<?> handle(AddRolesToUserQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}