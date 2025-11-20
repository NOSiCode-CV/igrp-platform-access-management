/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.m2m.interfaces.rest;

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
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.m2m.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "M2M", description = "Machine-to-Machine")
public class M2MController {

  
  private final CommandBus commandBus;

  public M2MController(CommandBus commandBus) {
          
          this.commandBus = commandBus;
  }
   @PostMapping(
   value = "m2m/sync/permissions"
  )
  @Operation(
    summary = "Sync permissions",
    description = "Sync permissions",
    responses = {
      @ApiResponse(
          responseCode = "204",
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
  
  public ResponseEntity<String> syncPermissions(@Valid @RequestBody List<PermissionDTO> syncPermissionsRequest
    )
  {

      final var command = new SyncPermissionsCommand(syncPermissionsRequest);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
   value = "m2m/sync/resources"
  )
  @Operation(
    summary = "Sync resources",
    description = "Sync resources",
    responses = {
      @ApiResponse(
          responseCode = "204",
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
  
  public ResponseEntity<String> syncResources(@Valid @RequestBody ResourceDTO syncResourcesRequest
    )
  {

      final var command = new SyncResourcesCommand(syncResourcesRequest);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
   value = "m2m/sync/applications"
  )
  @Operation(
    summary = "Sync applications",
    description = "Sync applications",
    responses = {
      @ApiResponse(
          responseCode = "204",
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
  
  public ResponseEntity<String> syncApplications(@Valid @RequestBody ApplicationDTO syncApplicationsRequest
    )
  {

      final var command = new SyncApplicationsCommand(syncApplicationsRequest);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

}