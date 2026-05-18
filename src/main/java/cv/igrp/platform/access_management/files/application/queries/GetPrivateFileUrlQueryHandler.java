package cv.igrp.platform.access_management.files.application.queries;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.filemanager.StorageService;
import lombok.Setter;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.files.application.dto.FileUrlDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class GetPrivateFileUrlQueryHandler implements QueryHandler<GetPrivateFileUrlQuery, ResponseEntity<FileUrlDTO>> {

    private final StorageService fileManagerService;

    @Setter
    @Value("${igrp.s3.aws-url-expiration-time}")
    private int urlExpirationTimeInSeconds;

    @Value("${igrp.s3.aws-endpoint}")
    private String awsEndpoint;

    @Value("${igrp.s3.aws-bucket}")
    private String awsBucket;

    public GetPrivateFileUrlQueryHandler(StorageService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    @IgrpQueryHandler
    public ResponseEntity<FileUrlDTO> handle(GetPrivateFileUrlQuery query) {
        var filePath = query.getFilePath();

        if (filePath == null || filePath.isBlank()) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FILE_PATH_REQUIRED);
        }

        var fileUrlDto = new FileUrlDTO();

        if (filePath.startsWith("private")) {
            // Private file: generate pre-signed URL with expiration
            LocalDateTime localExpiration = LocalDateTime.now()
                    .plusSeconds(urlExpirationTimeInSeconds);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            String expirationIso = localExpiration.format(formatter);

            fileUrlDto.setUrl(fileManagerService.getFileUrl(filePath));
            fileUrlDto.setExpiration(expirationIso);

        } else if (filePath.startsWith("public")) {
            // Public file: construct plain URL without expiration
            String url = String.format("%s/%s/%s", awsEndpoint, awsBucket, filePath);
            fileUrlDto.setUrl(url);
            fileUrlDto.setExpiration(null); // no expiration
        } else {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FILE_PRIVATE_URL_GENERATION_FAILED, filePath);
        }

        return ResponseEntity.ok(fileUrlDto);
    }
}