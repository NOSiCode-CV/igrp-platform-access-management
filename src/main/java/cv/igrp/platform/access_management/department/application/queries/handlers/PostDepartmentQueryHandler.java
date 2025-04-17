package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.PostDepartmentQuery;


@Service
public class PostDepartmentQueryHandler implements QueryHandler<PostDepartmentQuery, ResponseEntity<String>>{

   public PostDepartmentQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<String> handle(PostDepartmentQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}