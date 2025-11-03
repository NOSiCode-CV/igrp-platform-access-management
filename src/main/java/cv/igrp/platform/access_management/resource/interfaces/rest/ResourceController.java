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
import org.springframework.security.access.prepost.PreAuthorize;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.resource.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.resource.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import java.util.Map;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Resource", description = "Resource Management")
public class ResourceController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public ResourceController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
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

      final var query = new GetResourcesQuery(name, type, externalID, applicationCode, description);

      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);

      return response;
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

      final var query = new GetResourceByIdQuery(name);

      ResponseEntity<ResourceDTO> response = queryBus.handle(query);

      return response;
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

      final var command = new CreateResourceCommand(createResourceRequest);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
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

      final var command = new UpdateResourceCommand(updateResourceRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
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

      final var command = new DeleteResourceCommand(name);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
    value = "resources/{name}/items"
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

      final var command = new AddItemsCommand(addItemsRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "resources/{name}/items"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeItems",
    description = "DELETE method to handle operations for removeItems",
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
  
  public ResponseEntity<ResourceDTO> removeItems(@RequestBody List<String> removeItemsRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new RemoveItemsCommand(removeItemsRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
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

      final var command = new AddResourceCustomFieldsCommand(addResourceCustomFieldsRequest, name);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "resources/{name}/custom-fields"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeResourceCustomFields",
    description = "DELETE method to handle operations for removeResourceCustomFields",
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

      final var command = new RemoveResourceCustomFieldsCommand(removeResourceCustomFieldsRequest, name);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
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

      final var query = new GetResourceCustomFieldsQuery(name);

      ResponseEntity<Map<String, ?>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "resources/{name}/applications/{applicationCode}"
  )
  @Operation(
    summary = "POST method to handle operations for shareResourceToAnotherApplication",
    description = "POST method to handle operations for shareResourceToAnotherApplication",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Share a resource to another application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> shareResourceToAnotherApplication(
    @PathVariable(value = "name") String name,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new ShareResourceToAnotherApplicationCommand(name, applicationCode);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "resources/{name}/applications/{applicationCode}"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeResourceFromApplication",
    description = "DELETE method to handle operations for removeResourceFromApplication",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "Remove a resource from an application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> removeResourceFromApplication(
    @PathVariable(value = "name") String name,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new RemoveResourceFromApplicationCommand(name, applicationCode);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
    value = "resources/{name}/permissions"
  )
  @Operation(
    summary = "POST method to handle operations for addPermissionsToResource",
    description = "POST method to handle operations for addPermissionsToResource",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> addPermissionsToResource(@RequestBody List<String> addPermissionsToResourceRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new AddPermissionsToResourceCommand(addPermissionsToResourceRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "resources/{name}/permissions"
  )
  @Operation(
    summary = "DELETE method to handle operations for removePermissionsFromResource",
    description = "DELETE method to handle operations for removePermissionsFromResource",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceDTO> removePermissionsFromResource(@RequestBody List<String> removePermissionsFromResourceRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new RemovePermissionsFromResourceCommand(removePermissionsFromResourceRequest, name);

       ResponseEntity<ResourceDTO> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
    value = "resources/item/{name}/permissions"
  )
  @Operation(
    summary = "POST method to handle operations for addPermissionsToResourceItem",
    description = "POST method to handle operations for addPermissionsToResourceItem",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceItemDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceItemDTO> addPermissionsToResourceItem(@RequestBody List<String> addPermissionsToResourceItemRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new AddPermissionsToResourceItemCommand(addPermissionsToResourceItemRequest, name);

       ResponseEntity<ResourceItemDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "resources/item/{name}/permissions"
  )
  @Operation(
    summary = "DELETE method to handle operations for removePermissionsFromResourceItem",
    description = "DELETE method to handle operations for removePermissionsFromResourceItem",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceItemDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<ResourceItemDTO> removePermissionsFromResourceItem(@RequestBody List<String> removePermissionsFromResourceItemRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new RemovePermissionsFromResourceItemCommand(removePermissionsFromResourceItemRequest, name);

       ResponseEntity<ResourceItemDTO> response = commandBus.send(command);

       return response;
  }

}