package cv.igrp.platform.access_management.app.infrastructure.controller;

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
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.app.application.commands.commands.*;
import cv.igrp.platform.access_management.app.application.queries.queries.*;


import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import java.util.List;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Application", description = "Application Management")
public class ApplicationController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public ApplicationController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping(
    value = "applications"
  )
  @Operation(
    summary = "POST method to handle operations for createApplication",
    description = "POST method to handle operations for createApplication",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "The Application Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "ApplicationDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> createApplication(@Valid @RequestBody ApplicationDTO createApplicationRequest
    )
  {
      final var command = new CreateApplicationCommand(createApplicationRequest);
       ResponseEntity<ApplicationDTO> response = (ResponseEntity<ApplicationDTO>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @GetMapping(
    value = "applications"
  )
  @Operation(
    summary = "GET method to handle operations for getApplications",
    description = "GET method to handle operations for getApplications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Application Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "ApplicationDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> getApplications(
    )
  {
      final var query = new GetApplicationsQuery();
      ResponseEntity<ApplicationDTO> response = (ResponseEntity<ApplicationDTO>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @GetMapping(
    value = "applications/{id}/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationById",
    description = "GET method to handle operations for getApplicationById",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Application Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "ApplicationDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> getApplicationById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetApplicationByIdQuery(id);
      ResponseEntity<ApplicationDTO> response = (ResponseEntity<ApplicationDTO>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @PutMapping(
    value = "applications/{id}/{id}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateApplication",
    description = "PUT method to handle operations for updateApplication",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Application Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "ApplicationDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> updateApplication(
    @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdateApplicationCommand(id);
       ResponseEntity<ApplicationDTO> response = (ResponseEntity<ApplicationDTO>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @DeleteMapping(
    value = "applications/{id}/{id}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteApplication",
    description = "DELETE method to handle operations for deleteApplication",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No content",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> deleteApplication(
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeleteApplicationCommand(id);
       ResponseEntity<String> response = (ResponseEntity<String>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @PostMapping(
    value = "applicationsByIds"
  )
  @Operation(
    summary = "POST method to handle operations for getApplicationsByIds",
    description = "POST method to handle operations for getApplicationsByIds",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The list of Application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "ApplicationDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getApplicationsByIds(
    )
  {
      final var command = new GetApplicationsByIdsCommand();
       ResponseEntity<List<ApplicationDTO>> response = (ResponseEntity<List<ApplicationDTO>>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

}