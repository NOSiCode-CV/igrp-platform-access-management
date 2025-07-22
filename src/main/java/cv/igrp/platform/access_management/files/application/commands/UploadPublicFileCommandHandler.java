package cv.igrp.platform.access_management.files.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.files.application.commands.handler.UploadFileCommandHandler;
import cv.igrp.platform.access_management.files.application.constants.UploadType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class UploadPublicFileCommandHandler implements CommandHandler<UploadPublicFileCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UploadPublicFileCommandHandler.class);

   private final UploadFileCommandHandler uploadFileCommandHandler;

    public UploadPublicFileCommandHandler(UploadFileCommandHandler uploadFileCommandHandler) {
        this.uploadFileCommandHandler = uploadFileCommandHandler;
    }

    @IgrpCommandHandler
   public ResponseEntity<String> handle(UploadPublicFileCommand command) {

       var filePath = uploadFileCommandHandler.uploadFile(
               command.getFile(),
               command.getFolder(),
               UploadType.PUBLIC
       );

       return ResponseEntity.ok(filePath);

   }

}