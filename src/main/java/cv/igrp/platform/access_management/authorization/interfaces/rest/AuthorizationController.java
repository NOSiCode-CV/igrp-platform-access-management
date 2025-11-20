/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.authorization.interfaces.rest;

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
import cv.igrp.platform.access_management.authorization.application.commands.*;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckRequestDTO;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import java.util.List;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Authorization", description = "Checking authorization for action")
public class AuthorizationController {

  
  private final CommandBus commandBus;

  public AuthorizationController(CommandBus commandBus) {
          
          this.commandBus = commandBus;
  }
   @PostMapping(
   value = "authorize/check"
  )
  @Operation(
    summary = "Check authorization",
    description = "Check authorization",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionCheckResponseDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<PermissionCheckResponseDTO> checkAuthorization(@Valid @RequestBody PermissionCheckRequestDTO checkAuthorizationRequest
    )
  {

      final var command = new CheckAuthorizationCommand(checkAuthorizationRequest);

       ResponseEntity<PermissionCheckResponseDTO> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
   value = "authorize/batch-check"
  )
  @Operation(
    summary = "Batch check authorization",
    description = "Batch check authorization",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionCheckResponseDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionCheckResponseDTO>> batchCheckAuthorization(@Valid @RequestBody List<PermissionCheckRequestDTO> batchCheckAuthorizationRequest
    )
  {

      final var command = new BatchCheckAuthorizationCommand(batchCheckAuthorizationRequest);

       ResponseEntity<List<PermissionCheckResponseDTO>> response = commandBus.send(command);

       return response;
  }

}