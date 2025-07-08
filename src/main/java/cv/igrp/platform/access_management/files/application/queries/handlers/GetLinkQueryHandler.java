package cv.igrp.platform.access_management.files.application.queries.handlers;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.filemanager.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.files.application.queries.queries.GetLinkQuery;


@Service
public class GetLinkQueryHandler implements QueryHandler<GetLinkQuery, ResponseEntity<String>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetLinkQueryHandler.class);

  private final StorageService fileManagerService;

   public GetLinkQueryHandler(StorageService fileManagerService) {
       this.fileManagerService = fileManagerService;
   }

   @IgrpQueryHandler
   public ResponseEntity<String> handle(GetLinkQuery query) {

       var file = query.getId();

       if(file == null) {
           throw IgrpResponseStatusException.of(
                   HttpStatus.BAD_REQUEST,
                   "No ID provided",
                   "There's no ID provided. Please check and try again."
           );
       }

       return ResponseEntity.ok(fileManagerService.getFileUrl(file));

   }

}