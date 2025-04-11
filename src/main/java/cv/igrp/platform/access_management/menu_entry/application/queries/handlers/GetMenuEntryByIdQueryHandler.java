package cv.igrp.platform.access_management.menu_entry.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.GetMenuEntryByIdQuery;
import java.util.List;

@Service
public class GetMenuEntryByIdQueryHandler implements QueryHandler<GetMenuEntryByIdQuery, ResponseEntity<List<MenuEntryDTO>>>{

   public GetMenuEntryByIdQueryHandler() {

   }

   @IgrpQueryHandler
   public ResponseEntity<List<MenuEntryDTO>> handle(GetMenuEntryByIdQuery query) {
      // TODO: Implement the query handling logic here
      return null;
   }

}