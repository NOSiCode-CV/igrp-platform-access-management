package cv.igrp.platform.access_management.files.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.file_manager_core.service.FileManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.platform.access_management.files.application.commands.commands.UploadFileCommand;

import java.io.IOException;
import java.util.UUID;


@Service
public class UploadFileCommandHandler implements CommandHandler<UploadFileCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UploadFileCommandHandler.class);

   private final FileManagerService fileManagerService;

   public UploadFileCommandHandler(FileManagerService fileManagerService) {
      this.fileManagerService = fileManagerService;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(UploadFileCommand command) {

      var file = command.getFile();

      if(file == null) {
         throw new IgrpResponseStatusException(new IgrpProblem<>(
                 HttpStatus.BAD_REQUEST,
                 "No file uploaded",
                 "There's no file uploaded. Please check and try again."
         ));
      }

       try {

          UUID uuid = UUID.randomUUID();

          byte[] compressedImage = file.getBytes();

          var fileUrl = uuid + "_" + file.getOriginalFilename();

          fileManagerService.uploadFile(compressedImage, fileUrl, file.getContentType());

          return ResponseEntity.ok(fileUrl);

       } catch (Exception e) {
           throw new RuntimeException(e);
       }

   }

}