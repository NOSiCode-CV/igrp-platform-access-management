package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@Service
public class GetDepartmentsQueryHandler implements QueryHandler<GetDepartmentsQuery, ResponseEntity<DepartmentDTO>>{

   public GetDepartmentsQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<DepartmentDTO> handle(GetDepartmentsQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}