package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetDepartmentByIdQuery;


@Service
public class GetDepartmentByIdQueryHandler implements QueryHandler<GetDepartmentByIdQuery, ResponseEntity<Department>>{

   public GetDepartmentByIdQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<Department> handle(GetDepartmentByIdQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}