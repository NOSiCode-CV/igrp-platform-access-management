/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.users.interfaces.rest;

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
import cv.igrp.platform.access_management.users.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.users.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "User", description = "User")
public class UserController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public UserController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @GetMapping(
    value = "users/{username}"
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
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<IGRPUserDTO> getUser(
    @PathVariable(value = "username") String username)
  {

      final var query = new GetUserQuery(username);

      ResponseEntity<IGRPUserDTO> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "users/{username}/roles"
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
                  type = "")
          )
      ), 
      @ApiResponse(
          responseCode = "201",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<?> addRolesToUser(@RequestBody List<String> addRolesToUserRequest
    , @PathVariable(value = "username") String username)
  {

      final var command = new AddRolesToUserCommand(addRolesToUserRequest, username);

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "users/{username}/roles"
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
  
  public ResponseEntity<List<RoleDTO>> removeRolesFromUser(@RequestBody List<String> removeRolesFromUserRequest
    , @PathVariable(value = "username") String username)
  {

      final var command = new RemoveRolesFromUserCommand(removeRolesFromUserRequest, username);

       ResponseEntity<List<RoleDTO>> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "users/{username}/roles"
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
    @PathVariable(value = "username") String username)
  {

      final var query = new GetUserRolesQuery(username);

      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "users/list"
  )
  @Operation(
    summary = "POST method to handle operations for getUsers",
    description = "POST method to handle operations for getUsers",
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
  
  public ResponseEntity<List<IGRPUserDTO>> getUsers(@RequestBody List<Integer> getUsersRequest
    , @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "departmentCode", required = false) String departmentCode,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "username", required = false) String username,
    @RequestParam(value = "email", required = false) String email)
  {

      final var command = new GetUsersCommand(getUsersRequest, applicationCode, departmentCode, name, username, email);

       ResponseEntity<List<IGRPUserDTO>> response = commandBus.send(command);

       return response;
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
  
  public ResponseEntity<IGRPUserDTO> createUser(@Valid @RequestBody IGRPUserDTO createUserRequest
    )
  {

      final var command = new CreateUserCommand(createUserRequest);

       ResponseEntity<IGRPUserDTO> response = commandBus.send(command);

       return response;
  }

   @PutMapping(
    value = "users/{username}"
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
  
  public ResponseEntity<IGRPUserDTO> updateUser(@Valid @RequestBody IGRPUserDTO updateUserRequest
    , @PathVariable(value = "username") String username)
  {

      final var command = new UpdateUserCommand(updateUserRequest, username);

       ResponseEntity<IGRPUserDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "users/current"
  )
  @Operation(
    summary = "GET method to handle operations for getCurrentUser",
    description = "GET method to handle operations for getCurrentUser",
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
  
  public ResponseEntity<IGRPUserDTO> getCurrentUser(
    )
  {

      final var query = new GetCurrentUserQuery();

      ResponseEntity<IGRPUserDTO> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "users/invite"
  )
  @Operation(
    summary = "POST method to handle operations for inviteUser",
    description = "POST method to handle operations for inviteUser",
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
  
  public ResponseEntity<IGRPUserDTO> inviteUser(@Valid @RequestBody IGRPUserDTO inviteUserRequest
    )
  {

      final var command = new InviteUserCommand(inviteUserRequest);

       ResponseEntity<IGRPUserDTO> response = commandBus.send(command);

       return response;
  }

}