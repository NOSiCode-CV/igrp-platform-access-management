package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourcesQuery;
import java.util.List;

@Service
public class GetResourcesQueryHandler implements QueryHandler<GetResourcesQuery, ResponseEntity<List<ResourceDTO>>>{

   public GetResourcesQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<List<ResourceDTO>> handle(GetResourcesQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}