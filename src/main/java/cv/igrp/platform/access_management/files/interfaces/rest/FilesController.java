/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.files.interfaces.rest;

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
import org.springframework.security.access.prepost.PreAuthorize;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.files.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.files.application.commands.*;
import cv.igrp.platform.access_management.files.application.dto.FileUrlDTO;
import org.springframework.web.multipart.MultipartFile;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Files", description = "File Management")
public class FilesController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public FilesController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_USER_VIEW, T(Permission).IGRP_APPLICATION_VIEW)")
   @GetMapping(
   value = "files/url"
  )
  @Operation(
    summary = "Get private file url",
    description = "Any of these Permissions is required: [igrp.user.view,igrp.application.view]",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = FileUrlDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<FileUrlDTO> getPrivateFileUrl(
    @RequestParam(value = "filePath") String filePath)
  {

      final var query = new GetPrivateFileUrlQuery(filePath);

      ResponseEntity<FileUrlDTO> response = queryBus.handle(query);

      return response;
  }

   @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_USER_UPDATE, T(Permission).IGRP_APPLICATION_UPDATE)")
   @PostMapping(
   value = "files/public"
  )
  @Operation(
    summary = "Upload public file",
    description = "Any of these Permissions is required: [igrp.user.update,igrp.application.update]",
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
  
  public ResponseEntity<String> uploadPublicFile(
    @RequestParam(value = "file") MultipartFile file,
    @RequestParam(value = "folder") String folder)
  {

      final var command = new UploadPublicFileCommand(file, folder);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_USER_UPDATE, T(Permission).IGRP_APPLICATION_UPDATE)")
   @PostMapping(
   value = "files/private"
  )
  @Operation(
    summary = "Upload private file",
    description = "Any of these Permissions is required: [igrp.user.update,igrp.application.update]",
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
  
  public ResponseEntity<String> uploadPrivateFile(
    @RequestParam(value = "file") MultipartFile file,
    @RequestParam(value = "folder") String folder)
  {

      final var command = new UploadPrivateFileCommand(file, folder);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

}