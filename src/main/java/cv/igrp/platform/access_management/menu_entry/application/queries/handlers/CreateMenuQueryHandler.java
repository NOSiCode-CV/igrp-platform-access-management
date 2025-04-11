package cv.igrp.platform.access_management.menu_entry.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.CreateMenuQuery;


@Service
public class CreateMenuQueryHandler implements QueryHandler<CreateMenuQuery, ResponseEntity<MenuEntryDTO>>{

   public CreateMenuQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<MenuEntryDTO> handle(CreateMenuQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}