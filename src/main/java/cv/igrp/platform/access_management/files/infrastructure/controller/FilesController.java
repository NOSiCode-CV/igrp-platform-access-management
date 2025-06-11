package cv.igrp.platform.access_management.files.infrastructure.controller;

import cv.igrp.framework.stereotype.IgrpController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.files.application.commands.commands.*;
import cv.igrp.platform.access_management.files.application.queries.queries.*;


import org.springframework.web.multipart.MultipartFile;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Files", description = "File Management")
public class FilesController {

   private static final Logger LOGGER = LoggerFactory.getLogger(FilesController.class);

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public FilesController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping(
    value = "files/getLink/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getLink",
    description = "GET method to handle operations for getLink",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> getLink(
    @PathVariable(value = "id") String id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "FilesController", "getLink");
      final var query = new GetLinkQuery(id);
      ResponseEntity<String> response = queryBus.handle(query);
      LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "FilesController", "getLink");
      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "files/uploadFile"
  )
  @Operation(
    summary = "POST method to handle operations for uploadFile",
    description = "POST method to handle operations for uploadFile",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The file ID",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> uploadFile(
    @RequestParam(value = "file") MultipartFile file)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "FilesController", "uploadFile");
      final var command = new UploadFileCommand(file);
       ResponseEntity<String> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "FilesController", "uploadFile");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}