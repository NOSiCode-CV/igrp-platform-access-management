/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.permission.interfaces.rest;

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
import cv.igrp.platform.access_management.permission.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.permission.application.commands.*;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Permissions", description = "Permission Management")
public class PermissionsController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public PermissionsController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PostMapping(
    value = "permissions"
  )
  @Operation(
    summary = "POST method to handle operations for createPermission",
    description = "POST method to handle operations for createPermission",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<PermissionDTO> createPermission(@Valid @RequestBody PermissionDTO createPermissionRequest
    )
  {

      final var command = new CreatePermissionCommand(createPermissionRequest);

       ResponseEntity<PermissionDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "permissions/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getPermissionByID",
    description = "GET method to handle operations for getPermissionByID",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<PermissionDTO> getPermissionByID(
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetPermissionByIDQuery(id);

      ResponseEntity<PermissionDTO> response = queryBus.handle(query);

      return response;
  }

   @PutMapping(
    value = "permissions/{name}"
  )
  @Operation(
    summary = "PUT method to handle operations for updatePermission",
    description = "PUT method to handle operations for updatePermission",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<PermissionDTO> updatePermission(@Valid @RequestBody PermissionDTO updatePermissionRequest
    , @PathVariable(value = "name") String name)
  {

      final var command = new UpdatePermissionCommand(updatePermissionRequest, name);

       ResponseEntity<PermissionDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "permissions/{name}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deletePermission",
    description = "DELETE method to handle operations for deletePermission",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = boolean.class,
                  type = "boolean")
          )
      )
    }
  )
  
  public ResponseEntity<Boolean> deletePermission(
    @PathVariable(value = "name") String name)
  {

      final var command = new DeletePermissionCommand(name);

       ResponseEntity<Boolean> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "permissions/{name}/roles"
  )
  @Operation(
    summary = "GET method to handle operations for getRolesByPermissionID",
    description = "GET method to handle operations for getRolesByPermissionID",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getRolesByPermissionID(
    @PathVariable(value = "name") String name)
  {

      final var query = new GetRolesByPermissionIDQuery(name);

      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "permissions"
  )
  @Operation(
    summary = "GET method to handle operations for getPermissionByApplicationId",
    description = "GET method to handle operations for getPermissionByApplicationId",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionDTO>> getPermissionByApplicationId(
    @RequestParam(value = "resourceId", required = false) Integer resourceId,
    @RequestParam(value = "resourceName", required = false) String resourceName)
  {

      final var query = new GetPermissionByApplicationIdQuery(resourceId, resourceName);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "permissions/by-name/{name}"
  )
  @Operation(
    summary = "GET method to handle operations for getPermissionByName",
    description = "GET method to handle operations for getPermissionByName",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<PermissionDTO> getPermissionByName(
    @PathVariable(value = "name") String name)
  {

      final var query = new GetPermissionByNameQuery(name);

      ResponseEntity<PermissionDTO> response = queryBus.handle(query);

      return response;
  }

}