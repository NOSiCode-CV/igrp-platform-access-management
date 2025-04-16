package cv.igrp.platform.access_management.role.infrastructure.controller;

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
import cv.igrp.platform.access_management.role.application.commands.commands.*;
import cv.igrp.platform.access_management.role.application.queries.queries.*;


import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Roles", description = "Role Management")
public class RolesController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public RolesController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping(
    value = "roles"
  )
  @Operation(
    summary = "POST method to handle operations for createRole",
    description = "POST method to handle operations for createRole",
    responses = {
      @ApiResponse(
          responseCode = "201",
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
  
  public ResponseEntity<RoleDTO> createRole( @Valid @RequestBody RoleDTO createRoleRequest
    )
  {
      final var command = new CreateRoleCommand(createRoleRequest);
       ResponseEntity<RoleDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "roles"
  )
  @Operation(
    summary = "GET method to handle operations for getRoles",
    description = "GET method to handle operations for getRoles",
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
  
  public ResponseEntity<List<RoleDTO>> getRoles(
    )
  {
      final var query = new GetRolesQuery();
      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "roles/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getRoleById",
    description = "GET method to handle operations for getRoleById",
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
  
  public ResponseEntity<RoleDTO> getRoleById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetRoleByIdQuery(id);
      ResponseEntity<RoleDTO> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PutMapping(
    value = "roles/{id}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateRole",
    description = "PUT method to handle operations for updateRole",
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
  
  public ResponseEntity<RoleDTO> updateRole( @Valid @RequestBody RoleDTO updateRoleRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdateRoleCommand(updateRoleRequest, id);
       ResponseEntity<RoleDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @DeleteMapping(
    value = "roles/{id}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteRole",
    description = "DELETE method to handle operations for deleteRole",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = Boolean.class,
                  type = "Boolean")
          )
      )
    }
  )
  
  public ResponseEntity<Boolean> deleteRole(
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeleteRoleCommand(id);
       ResponseEntity<Boolean> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PostMapping(
    value = "roles/{id}/removePermissions"
  )
  @Operation(
    summary = "POST method to handle operations for RemovePermissions",
    description = "POST method to handle operations for RemovePermissions",
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
  
  public ResponseEntity<List<PermissionDTO>> removePermissions(  @RequestBody List<Integer> removePermissionsRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new RemovePermissionsCommand(removePermissionsRequest, id);
       ResponseEntity<List<PermissionDTO>> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "roles/{id}/permissions"
  )
  @Operation(
    summary = "GET method to handle operations for GetPermissionsByRoleId",
    description = "GET method to handle operations for GetPermissionsByRoleId",
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
  
  public ResponseEntity<List<PermissionDTO>> getPermissionsByRoleId(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetPermissionsByRoleIdQuery(id);
      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PostMapping(
    value = "roles/{id}/addPermissions"
  )
  @Operation(
    summary = "POST method to handle operations for addPermissions",
    description = "POST method to handle operations for addPermissions",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "PermissionDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionDTO>> addPermissions(  @RequestBody List<Integer> addPermissionsRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new AddPermissionsCommand(addPermissionsRequest, id);
       ResponseEntity<List<PermissionDTO>> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

}