/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.global_configuration.interfaces.rest;

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
import cv.igrp.platform.access_management.global_configuration.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.global_configuration.application.commands.*;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "GlobalConfiguration", description = "Global Configuration")
public class GlobalConfigurationController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public GlobalConfigurationController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATIONS_CUSTOMIZE)")
   @PostMapping(
   value = "global-configuration"
  )
  @Operation(
    summary = "Set global configuration",
    description = "This Permission is required: igrp.applications.customize",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = GlobalConfigurationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<GlobalConfigurationDTO> setGlobalConfiguration(@Valid @RequestBody GlobalConfigurationDTO setGlobalConfigurationRequest
    )
  {

      final var command = new SetGlobalConfigurationCommand(setGlobalConfigurationRequest);

       ResponseEntity<GlobalConfigurationDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "global-configuration"
  )
  @Operation(
    summary = "Get global configuration",
    description = "Get global configuration",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = GlobalConfigurationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<GlobalConfigurationDTO> getGlobalConfiguration(
    @RequestParam(value = "type") String type)
  {

      final var query = new GetGlobalConfigurationQuery(type);

      ResponseEntity<GlobalConfigurationDTO> response = queryBus.handle(query);

      return response;
  }

}