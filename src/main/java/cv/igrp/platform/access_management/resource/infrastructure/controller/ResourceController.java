package cv.igrp.platform.access_management.resource.infrastructure.controller;

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
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.queries.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Resource", description = "Resource Management")
public class ResourceController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public ResourceController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping(
    value = "resources"
  )
  @Operation(
    summary = "GET method to handle operations for getResources",
    description = "GET method to handle operations for getResources",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "A List of Resources",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ResourceDTO>> getResources(
    @RequestParam(value = "applicationId", required = false) Integer applicationId,
    @RequestParam(value = "name", required = false) String name)
  {
      final var query = new GetResourcesQuery(applicationId, name);
      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "resources/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getResourceById",
    description = "GET method to handle operations for getResourceById",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Resource Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> getResourceById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetResourceByIdQuery(id);
      ResponseEntity<ResourceDTO> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PostMapping(
    value = "resources"
  )
  @Operation(
    summary = "POST method to handle operations for createResource",
    description = "POST method to handle operations for createResource",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "The Created Resource",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "ResourceDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> createResource( @Valid @RequestBody ResourceDTO createResourceRequest
    )
  {
      final var command = new CreateResourceCommand(createResourceRequest);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PutMapping(
    value = "resources/{id}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateResource",
    description = "PUT method to handle operations for updateResource",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Updated Resource Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> updateResource( @Valid @RequestBody ResourceDTO updateResourceRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdateResourceCommand(updateResourceRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @DeleteMapping(
    value = "resources/{id}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteResource",
    description = "DELETE method to handle operations for deleteResource",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No Content",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> deleteResource(
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeleteResourceCommand(id);
       ResponseEntity<String> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/addItems"
  )
  @Operation(
    summary = "POST method to handle operations for addItems",
    description = "POST method to handle operations for addItems",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Resource Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> addItems(@RequestBody List<ResourceItemDTO> addItemsRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new AddItemsCommand(addItemsRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/removeItems"
  )
  @Operation(
    summary = "POST method to handle operations for removeItems",
    description = "POST method to handle operations for removeItems",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Resource Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "ResourceDTO")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> removeItems(  @RequestBody List<Integer> removeItemsRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new RemoveItemsCommand(removeItemsRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

}