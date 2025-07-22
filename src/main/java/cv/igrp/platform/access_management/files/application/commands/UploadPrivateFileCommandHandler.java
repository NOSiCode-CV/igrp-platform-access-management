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
public class UploadPrivateFileCommandHandler implements CommandHandler<UploadPrivateFileCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UploadPrivateFileCommandHandler.class);

   private final UploadFileCommandHandler uploadFileCommandHandler;

    public UploadPrivateFileCommandHandler(UploadFileCommandHandler uploadFileCommandHandler) {
        this.uploadFileCommandHandler = uploadFileCommandHandler;
    }

    @IgrpCommandHandler
   public ResponseEntity<String> handle(UploadPrivateFileCommand command) {

       var filePath = uploadFileCommandHandler.uploadFile(
               command.getFile(),
               command.getFolder(),
               UploadType.PRIVATE
       );

       return ResponseEntity.ok(filePath);
   }

}