/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.resource.interfaces.rest;

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
import cv.igrp.platform.access_management.resource.application.commands.*;
import cv.igrp.platform.access_management.resource.application.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import java.util.Map;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Resource", description = "Resource Management")
public class ResourceController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceController.class);

  
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
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "type", required = false) String type,
    @RequestParam(value = "externalID", required = false) String externalID,
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "description", required = false) String description)
  {

      LOGGER.debug("Operation started");

      final var query = new GetResourcesQuery(name, type, externalID, applicationCode, description);

      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "resources/{name}"
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
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetResourceByIdQuery(name);

      ResponseEntity<ResourceDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> createResource(@Valid @RequestBody ResourceDTO createResourceRequest
    )
  {

      LOGGER.debug("Operation started");

      final var command = new CreateResourceCommand(createResourceRequest);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PutMapping(
    value = "resources/{name}"
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
  
  public ResponseEntity<ResourceDTO> updateResource(@Valid @RequestBody ResourceDTO updateResourceRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new UpdateResourceCommand(updateResourceRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @DeleteMapping(
    value = "resources/{name}"
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
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new DeleteResourceCommand(name);

       ResponseEntity<String> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{name}/add-items"
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
  
  public ResponseEntity<ResourceDTO> addItems(@Valid @RequestBody List<ResourceItemDTO> addItemsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new AddItemsCommand(addItemsRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{name}/remove-items"
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
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> removeItems(@RequestBody List<Integer> removeItemsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new RemoveItemsCommand(removeItemsRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{name}/custom-fields"
  )
  @Operation(
    summary = "POST method to handle operations for addResourceCustomFields",
    description = "POST method to handle operations for addResourceCustomFields",
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
  
  public ResponseEntity<String> addResourceCustomFields(@RequestBody Map<String, ?> addResourceCustomFieldsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new AddResourceCustomFieldsCommand(addResourceCustomFieldsRequest, name);

       ResponseEntity<String> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{name}/custom-fields/remove"
  )
  @Operation(
    summary = "POST method to handle operations for removeResourceCustomFields",
    description = "POST method to handle operations for removeResourceCustomFields",
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
  
  public ResponseEntity<String> removeResourceCustomFields(@RequestBody List<String> removeResourceCustomFieldsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new RemoveResourceCustomFieldsCommand(removeResourceCustomFieldsRequest, name);

       ResponseEntity<String> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "/resources/{name}/custom-fields"
  )
  @Operation(
    summary = "GET method to handle operations for getResourceCustomFields",
    description = "GET method to handle operations for getResourceCustomFields",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Resource Custom Fields",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<Map<String, ?>> getResourceCustomFields(
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetResourceCustomFieldsQuery(name);

      ResponseEntity<Map<String, ?>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}