package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourceByIdQuery;


@Service
public class GetResourceByIdQueryHandler implements QueryHandler<GetResourceByIdQuery, ResponseEntity<ResourceDTO>>{

   public GetResourceByIdQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<ResourceDTO> handle(GetResourceByIdQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}