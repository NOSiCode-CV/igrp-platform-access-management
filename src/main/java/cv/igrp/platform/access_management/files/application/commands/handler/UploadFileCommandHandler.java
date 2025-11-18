package cv.igrp.platform.access_management.files.application.commands.handler;

import cv.igrp.platform.access_management.files.application.constants.UploadType;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.filemanager.StorageService;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class UploadFileCommandHandler {

   private final AuthenticationHelper authenticationHelper;
   private final StorageService storageService;

    public UploadFileCommandHandler(AuthenticationHelper authenticationHelper, StorageService storageService) {
        this.authenticationHelper = authenticationHelper;
        this.storageService = storageService;
    }

   public String uploadFile(MultipartFile file, String folder, UploadType uploadType) {
      if (file == null || file.isEmpty()) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST,
                 "No file uploaded",
                 "There's no file uploaded. Please check and try again."
         );
      }

      try {
         String userName = authenticationHelper.getSub();
         String originalFilename = file.getOriginalFilename();
         String filePath = String.format(
                 "%s/%s/%s/%s_%s",
                 uploadType.name().toLowerCase(), // "public" ou "private"
                 folder,
                 userName,
                 UUID.randomUUID(),
                 originalFilename
         );

         byte[] fileBytes = file.getBytes();

         if (uploadType == UploadType.PUBLIC) {
            storageService.uploadPublicFile(fileBytes, filePath, file.getContentType());
         } else {
            storageService.uploadFile(fileBytes, filePath, file.getContentType());
         }

         return filePath;

      } catch (IOException e) {
         throw new RuntimeException("Error reading file content", e);
      } catch (Exception e) {
         throw new RuntimeException("Unexpected error while uploading file", e);
      }
   }

}