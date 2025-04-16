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


import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "User", description = "User")
public class UserController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public UserController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping(
    value = "users/{id}/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getUser",
    description = "GET method to handle operations for getUser",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPUserDTO.class,
                  type = "IGRPUserDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<IGRPUserDTO>> getUser(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetUserQuery(id);
      ResponseEntity<List<IGRPUserDTO>> response = (ResponseEntity<List<IGRPUserDTO>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @PostMapping(
    value = "users/{id}/addRoles/{id}"
  )
  @Operation(
    summary = "POST method to handle operations for AddRolesToUser",
    description = "POST method to handle operations for AddRolesToUser",
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
      ), 
      @ApiResponse(
          responseCode = "201",
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
  
  public ResponseEntity<?> addRolesToUser( @Valid @RequestBody RoleUserDTO addRolesToUserRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new AddRolesToUserCommand(addRolesToUserRequest, id);
       ResponseEntity<?> response = (ResponseEntity<?>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @DeleteMapping(
    value = "users/{id}/removeRoles/{id}"
  )
  @Operation(
    summary = "DELETE method to handle operations for RemoveRolesFromUser",
    description = "DELETE method to handle operations for RemoveRolesFromUser",
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
  
  public ResponseEntity<List<Role>> removeRolesFromUser( @Valid @RequestBody RoleDTO removeRolesFromUserRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new RemoveRolesFromUserCommand(removeRolesFromUserRequest, id);
       ResponseEntity<List<Role>> response = (ResponseEntity<List<Role>>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @GetMapping(
    value = "users/{id}/roles/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getUserRoles",
    description = "GET method to handle operations for getUserRoles",
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
  
  public ResponseEntity<List<Role>> getUserRoles(
    @RequestParam(value = "applicationId") Integer applicationId, @PathVariable(value = "id") Integer id)
  {
      final var query = new GetUserRolesQuery(applicationId, id);
      ResponseEntity<List<Role>> response = (ResponseEntity<List<Role>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @GetMapping(
    value = "users"
  )
  @Operation(
    summary = "GET method to handle operations for getUsers",
    description = "GET method to handle operations for getUsers",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPUserDTO.class,
                  type = "IGRPUserDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<IGRPUserDTO>> getUsers(
    @RequestParam(value = "applicationId") Integer applicationId,
    @RequestParam(value = "departmentId") Integer departmentId,
    @RequestParam(value = "name") String name,
    @RequestParam(value = "username") String username,
    @RequestParam(value = "email") String email)
  {
      final var query = new GetUsersQuery(applicationId, departmentId, name, username, email);
      ResponseEntity<List<IGRPUserDTO>> response = (ResponseEntity<List<IGRPUserDTO>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

}