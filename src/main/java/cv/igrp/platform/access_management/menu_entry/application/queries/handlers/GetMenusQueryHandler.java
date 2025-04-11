package cv.igrp.platform.access_management.menu_entry.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.GetMenusQuery;


@Service
public class GetMenusQueryHandler implements QueryHandler<GetMenusQuery, ResponseEntity<MenuEntryDTO>>{

   public GetMenusQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<MenuEntryDTO> handle(GetMenusQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}