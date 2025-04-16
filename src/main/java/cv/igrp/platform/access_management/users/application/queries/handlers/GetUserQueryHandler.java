package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUserQuery;
import java.util.List;

@Service
public class GetUserQueryHandler implements QueryHandler<GetUserQuery, ResponseEntity<List<IGRPUserDTO>>>{

   public GetUserQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<List<IGRPUserDTO>> handle(GetUserQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}