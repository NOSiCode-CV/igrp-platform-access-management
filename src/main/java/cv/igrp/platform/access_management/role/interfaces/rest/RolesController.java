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
import org.springframework.security.access.prepost.PreAuthorize;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.role.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.role.application.commands.*;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Roles", description = "Role Management")
public class RolesController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public RolesController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
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

      final var command = new CreateRoleCommand(createRoleRequest);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
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
    @RequestParam(value = "code", required = false) String code)
  {

      final var query = new GetRolesQuery(departmentCode, code);

      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);

      return response;
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
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "id") Integer id)
  {

      final var query = new GetRoleByIdQuery(departmentCode, id);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      return response;
  }

   @PutMapping(
   value = "roles/{code}"
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
    , @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var command = new UpdateRoleCommand(updateRoleRequest, departmentCode, code);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "roles/{code}"
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
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var command = new DeleteRoleCommand(departmentCode, code);

       ResponseEntity<Boolean> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "roles/{code}/permissions"
  )
  @Operation(
    summary = "DELETE method to handle operations for RemovePermissions",
    description = "DELETE method to handle operations for RemovePermissions",
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
    , @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var command = new RemovePermissionsCommand(removePermissionsRequest, departmentCode, code);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "roles/{code}/permissions"
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
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetPermissionsByRoleIdQuery(departmentCode, code);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "roles/{code}/permissions"
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
    , @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var command = new AddPermissionsCommand(addPermissionsRequest, departmentCode, code);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "roles/by-code/{code}"
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
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetRolesByNameQuery(departmentCode, code);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "roles/{code}/permissions/available"
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
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetAvailablePermissionsForRolesQuery(departmentCode, code);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      return response;
  }

}