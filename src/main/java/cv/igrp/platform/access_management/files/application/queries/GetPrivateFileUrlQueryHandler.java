package cv.igrp.platform.access_management.files.application.queries;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.filemanager.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.files.application.dto.FileUrlDTO;

import java.time.Instant;

@Component
public class GetPrivateFileUrlQueryHandler implements QueryHandler<GetPrivateFileUrlQuery, ResponseEntity<FileUrlDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPrivateFileUrlQueryHandler.class);

  private final StorageService fileManagerService;

  @Value("${igrp.minio.url-expiration-time}")
  private int urlExpirationTimeInSeconds;

  public GetPrivateFileUrlQueryHandler(StorageService fileManagerService) {
    this.fileManagerService = fileManagerService;
  }

   @IgrpQueryHandler
  public ResponseEntity<FileUrlDTO> handle(GetPrivateFileUrlQuery query) {
     var privateFilePath = query.getPrivateFilePath();

     if(privateFilePath == null || privateFilePath.isEmpty()) {
       throw IgrpResponseStatusException.of(
               HttpStatus.BAD_REQUEST,
               "No path provided",
               "There's no path provided. Please check and try again."
       );
     }

   Instant expirationInstant = Instant.now().plusSeconds(urlExpirationTimeInSeconds);
   String expirationIso = expirationInstant.toString();

     var fileUrlDto = new FileUrlDTO();
     fileUrlDto.setUrl(fileManagerService.getFileUrl(privateFilePath));
     fileUrlDto.setExpiration(expirationIso);

     return ResponseEntity.ok(fileUrlDto);
  }

}