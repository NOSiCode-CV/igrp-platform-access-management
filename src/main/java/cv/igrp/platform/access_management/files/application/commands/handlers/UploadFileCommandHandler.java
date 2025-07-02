package cv.igrp.platform.access_management.files.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.platform.filemanager.StorageService;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.platform.access_management.files.application.commands.commands.UploadFileCommand;

import java.util.UUID;


@Service
public class UploadFileCommandHandler implements CommandHandler<UploadFileCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UploadFileCommandHandler.class);

   private final StorageService fileManagerService;

   public UploadFileCommandHandler(StorageService fileManagerService) {
      this.fileManagerService = fileManagerService;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(UploadFileCommand command) {

      var file = command.getFile();

      if(file == null) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST,
                 "No file uploaded",
                 "There's no file uploaded. Please check and try again."
         );
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