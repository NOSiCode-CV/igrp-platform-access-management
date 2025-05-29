package cv.igrp.platform.access_management.global_configuration.infrastructure.controller;

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
import cv.igrp.platform.access_management.global_configuration.application.commands.commands.*;
import cv.igrp.platform.access_management.global_configuration.application.queries.queries.*;


import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "GlobalConfiguration", description = "global configuration")
public class GlobalConfigurationController {

   private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigurationController.class);

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public GlobalConfigurationController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping(
    value = "globalConfiguration"
  )
  @Operation(
    summary = "POST method to handle operations for setGlobalConfiguration",
    description = "POST method to handle operations for setGlobalConfiguration",
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
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "GlobalConfigurationController", "setGlobalConfiguration");
      final var command = new SetGlobalConfigurationCommand(setGlobalConfigurationRequest);
       ResponseEntity<GlobalConfigurationDTO> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "GlobalConfigurationController", "setGlobalConfiguration");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "globalConfiguration"
  )
  @Operation(
    summary = "GET method to handle operations for getGlobalConfiguration",
    description = "GET method to handle operations for getGlobalConfiguration",
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
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "GlobalConfigurationController", "getGlobalConfiguration");
      final var query = new GetGlobalConfigurationQuery(type);
      ResponseEntity<GlobalConfigurationDTO> response = queryBus.handle(query);
      LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "GlobalConfigurationController", "getGlobalConfiguration");
      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}