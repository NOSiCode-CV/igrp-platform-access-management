package cv.igrp.platform.access_management.m2m.application.queries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Component
public class GetUsersForBusinessQueryHandler implements QueryHandler<GetUsersForBusinessQuery, ResponseEntity<List<IGRPUserDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetUsersForBusinessQueryHandler.class);


  public GetUsersForBusinessQueryHandler() {

  }

   @IgrpQueryHandler
  public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersForBusinessQuery query) {
    // TODO: Implement the query handling logic here
    return null;
  }

}