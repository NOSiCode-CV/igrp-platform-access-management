package cv.igrp.platform.access_management.permission.infrastructure.controller;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
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
import cv.igrp.platform.access_management.permission.application.commands.commands.*;
import cv.igrp.platform.access_management.permission.application.queries.queries.*;


import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import java.util.List;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Permissions", description = "Permission Management")
public class PermissionsController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public PermissionsController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
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
  
  public ResponseEntity<PermissionDTO> createPermission( @Valid @RequestBody PermissionDTO createPermissionRequest
    )
  {
      final var command = new CreatePermissionCommand(createPermissionRequest);
       ResponseEntity<PermissionDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
    @RequestParam(value = "applicationId") Integer applicationId)
  {
      final var query = new GetPermissionByApplicationIdQuery(applicationId);
      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PutMapping(
    value = "permissions/{id}"
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
  
  public ResponseEntity<PermissionDTO> updatePermission( @Valid @RequestBody PermissionDTO updatePermissionRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdatePermissionCommand(updatePermissionRequest, id);
       ResponseEntity<PermissionDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @DeleteMapping(
    value = "permissions/{id}"
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
                  implementation = Boolean.class,
                  type = "Boolean")
          )
      )
    }
  )
  
  public ResponseEntity<Boolean> deletePermission(
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeletePermissionCommand(id);
       ResponseEntity<Boolean> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "permissions/{id}/roles"
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
                  type = "RoleDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getRolesByPermissionID(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetRolesByPermissionIDQuery(id);
      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

}