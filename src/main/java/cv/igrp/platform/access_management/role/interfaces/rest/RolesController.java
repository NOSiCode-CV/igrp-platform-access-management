/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.role.interfaces.rest;

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
import org.springframework.security.access.prepost.PreAuthorize;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.role.application.commands.*;
import cv.igrp.platform.access_management.role.application.queries.*;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Roles", description = "Role Management")
public class RolesController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RolesController.class);

  
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
  
  public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO createRoleRequest
    )
  {

      LOGGER.debug("Operation started");

      final var command = new CreateRoleCommand(createRoleRequest);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
    @RequestParam(value = "departmentCode", required = false) String departmentCode,
    @RequestParam(value = "name", required = false) String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetRolesQuery(departmentCode, name);

      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetRoleByIdQuery(id);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PutMapping(
    value = "roles/{name}"
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
  
  public ResponseEntity<RoleDTO> updateRole(@Valid @RequestBody RoleDTO updateRoleRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new UpdateRoleCommand(updateRoleRequest, name);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @DeleteMapping(
    value = "roles/{name}"
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
                  implementation = boolean.class,
                  type = "boolean")
          )
      )
    }
  )
  
  public ResponseEntity<Boolean> deleteRole(
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new DeleteRoleCommand(name);

       ResponseEntity<Boolean> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "roles/{name}/removePermissions"
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
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDTO> removePermissions(@RequestBody List<String> removePermissionsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new RemovePermissionsCommand(removePermissionsRequest, name);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "roles/{name}/permissions"
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
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetPermissionsByRoleIdQuery(name);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "roles/{name}/addPermissions"
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
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDTO> addPermissions(@RequestBody List<String> addPermissionsRequest
    , @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var command = new AddPermissionsCommand(addPermissionsRequest, name);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "roles/by-name/{name}"
  )
  @Operation(
    summary = "GET method to handle operations for getRolesByName",
    description = "GET method to handle operations for getRolesByName",
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
  
  public ResponseEntity<RoleDTO> getRolesByName(
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetRolesByNameQuery(name);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "roles/{name}/permissions/available"
  )
  @Operation(
    summary = "GET method to handle operations for getAvailablePermissionsForRoles",
    description = "GET method to handle operations for getAvailablePermissionsForRoles",
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
  
  public ResponseEntity<List<PermissionDTO>> getAvailablePermissionsForRoles(
    @PathVariable(value = "name") String name)
  {

      LOGGER.debug("Operation started");

      final var query = new GetAvailablePermissionsForRolesQuery(name);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}