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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.queries.queries.*;


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
    @RequestParam(value = "applicationId", required = false) Integer applicationId,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "type", required = false) String type,
    @RequestParam(value = "externalID", required = false) String externalID,
    @RequestParam(value = "applicationCode", required = false) String applicationCode)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "getResources");
      final var query = new GetResourcesQuery(applicationId, name, type, externalID, applicationCode);
      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);
      LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "getResources");
      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "getResourceById");
      final var query = new GetResourceByIdQuery(id);
      ResponseEntity<ResourceDTO> response = queryBus.handle(query);
      LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "getResourceById");
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
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "createResource");
      final var command = new CreateResourceCommand(createResourceRequest);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "createResource");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
  
  public ResponseEntity<ResourceDTO> updateResource(@Valid @RequestBody ResourceDTO updateResourceRequest
    , @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "updateResource");
      final var command = new UpdateResourceCommand(updateResourceRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "updateResource");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "deleteResource");
      final var command = new DeleteResourceCommand(id);
       ResponseEntity<String> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "deleteResource");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/add-items"
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
    , @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "addItems");
      final var command = new AddItemsCommand(addItemsRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "addItems");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/remove-items"
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
    , @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "removeItems");
      final var command = new RemoveItemsCommand(removeItemsRequest, id);
       ResponseEntity<ResourceDTO> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "removeItems");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/custom-fields"
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
    , @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "addResourceCustomFields");
      final var command = new AddResourceCustomFieldsCommand(addResourceCustomFieldsRequest, id);
       ResponseEntity<String> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "addResourceCustomFields");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "resources/{id}/custom-fields/remove"
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
    , @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "removeResourceCustomFields");
      final var command = new RemoveResourceCustomFieldsCommand(removeResourceCustomFieldsRequest, id);
       ResponseEntity<String> response = commandBus.send(command);
       LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "removeResourceCustomFields");
        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "/resources/{id}/custom-fields"
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
    @PathVariable(value = "id") Integer id)
  {
      LOGGER.debug("Operation started - Endpoint: {}, Action: {}", "ResourceController", "getResourceCustomFields");
      final var query = new GetResourceCustomFieldsQuery(id);
      ResponseEntity<Map<String, ?>> response = queryBus.handle(query);
      LOGGER.debug("Operation finished - Endpoint: {}, Action: {}", "ResourceController", "getResourceCustomFields");
      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}