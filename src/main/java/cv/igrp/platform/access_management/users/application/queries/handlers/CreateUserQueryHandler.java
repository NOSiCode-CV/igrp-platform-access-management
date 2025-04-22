package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.CreateUserQuery;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Service
public class CreateUserQueryHandler implements QueryHandler<CreateUserQuery, ResponseEntity<IGRPUserDTO>>{

   public CreateUserQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<IGRPUserDTO> handle(CreateUserQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}