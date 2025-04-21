package cv.igrp.platform.access_management.users.infrastructure.controller;

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
import cv.igrp.platform.access_management.users.application.commands.commands.*;
import cv.igrp.platform.access_management.users.application.queries.queries.*;


import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

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
  
  public ResponseEntity<IGRPUserDTO> getUser(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetUserQuery(id);
      ResponseEntity<IGRPUserDTO> response = (ResponseEntity<IGRPUserDTO>) queryBus.handle(query);
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
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> removeRolesFromUser( @Valid @RequestBody RoleDTO removeRolesFromUserRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new RemoveRolesFromUserCommand(removeRolesFromUserRequest, id);
       ResponseEntity<List<RoleDTO>> response = (ResponseEntity<List<RoleDTO>>) commandBus.send(command);
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
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getUserRoles(
    @RequestParam(value = "applicationId") Integer applicationId, @PathVariable(value = "id") Integer id)
  {
      final var query = new GetUserRolesQuery(applicationId, id);
      ResponseEntity<List<RoleDTO>> response = (ResponseEntity<List<RoleDTO>>) queryBus.handle(query);
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
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<IGRPUserDTO>> getUsers(
    @RequestParam(value = "applicationId", required = false) Integer applicationId,
    @RequestParam(value = "departmentId", required = false) Integer departmentId,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "username", required = false) String username,
    @RequestParam(value = "email", required = false) String email)
  {
      final var query = new GetUsersQuery(applicationId, departmentId, name, username, email);
      ResponseEntity<List<IGRPUserDTO>> response = (ResponseEntity<List<IGRPUserDTO>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @PostMapping(
    value = "users"
  )
  @Operation(
    summary = "POST method to handle operations for createUser",
    description = "POST method to handle operations for createUser",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPUserDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<IGRPUserDTO> createUser( @Valid @RequestBody IGRPUserDTO createUserRequest
    )
  {
      final var command = new CreateUserCommand(createUserRequest);
       ResponseEntity<IGRPUserDTO> response = (ResponseEntity<IGRPUserDTO>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @PutMapping(
    value = "users"
  )
  @Operation(
    summary = "PUT method to handle operations for updateUser",
    description = "PUT method to handle operations for updateUser",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPUserDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<IGRPUserDTO> updateUser( @Valid @RequestBody IGRPUserDTO updateUserRequest
    )
  {
      final var command = new UpdateUserCommand(updateUserRequest);
       ResponseEntity<IGRPUserDTO> response = (ResponseEntity<IGRPUserDTO>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

}