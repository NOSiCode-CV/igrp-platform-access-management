/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

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
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.application.dto.InviteUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(
    name = "Users",
    description = "User"
)
public class UserController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public UserController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}"
  )
  @Operation(
    summary = "Get user",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserQuery(id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @PostMapping(
   value = "users/{id}/departments/{departmentCode}/roles"
  )
  @Operation(
    summary = "Add roles to user",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "")
          )
      ), 
      @ApiResponse(
          responseCode = "201",
          
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
    , @PathVariable(value = "id") Integer id,@PathVariable(value = "departmentCode") String departmentCode)
  {

      final var command = new AddRolesToUserCommand(addRolesToUserRequest, id, departmentCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @DeleteMapping(
   value = "users/{id}/departments/{departmentCode}/roles"
  )
  @Operation(
    summary = "Remove roles from user",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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
    , @PathVariable(value = "id") Integer id,@PathVariable(value = "departmentCode") String departmentCode)
  {

      final var command = new RemoveRolesFromUserCommand(removeRolesFromUserRequest, id, departmentCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/roles"
  )
  @Operation(
    summary = "Get user roles",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserRolesQuery(id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/permissions"
  )
  @Operation(
    summary = "Get user permissions",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionDTO>> getUserPermissions(
    @RequestParam(value = "roleCode", required = false) String roleCode, @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserPermissionsQuery(roleCode, id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_LIST)")
   @GetMapping(
   value = "users"
  )
  @Operation(
    summary = "Get users",
    description = "This Permission is required: igrp.users.list",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "departmentCode", required = false) String departmentCode,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "id", required = false) Integer id,
    @RequestParam(value = "email", required = false) String email)
  {

      final var query = new GetUsersQuery(applicationCode, departmentCode, name, id, email);

      return queryBus.handle(query);

  }

   @PostMapping(
   value = "users/invite/response"
  )
  @Operation(
    summary = "Respond user invitation",
    description = "Respond user invitation",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> respondUserInvitation(@Valid @RequestBody UserInvitationResponseDTO respondUserInvitationRequest
    , @RequestParam(value = "token") String token)
  {

      final var command = new RespondUserInvitationCommand(respondUserInvitationRequest, token);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_UPDATE)")
   @PutMapping(
   value = "users/{id}"
  )
  @Operation(
    summary = "Update user",
    description = "This Permission is required: igrp.users.update",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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
    , @PathVariable(value = "id") Integer id)
  {

      final var command = new UpdateUserCommand(updateUserRequest, id);

      return commandBus.send(command);

  }

   @GetMapping(
   value = "users/me"
  )
  @Operation(
    summary = "Get current user",
    description = "Get current user",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
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

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_CREATE)")
   @PostMapping(
   value = "users/invite"
  )
  @Operation(
    summary = "Invite user",
    description = "This Permission is required: igrp.users.create",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> inviteUser(@Valid @RequestBody InviteUserDTO inviteUserRequest
    )
  {

      final var command = new InviteUserCommand(inviteUserRequest);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @DeleteMapping(
   value = "users/invite/{id}"
  )
  @Operation(
    summary = "Cancel user invitation",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> cancelUserInvitation(
    @PathVariable(value = "id") Integer id)
  {

      final var command = new CancelUserInvitationCommand(id);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @PutMapping(
   value = "users/invite/{id}"
  )
  @Operation(
    summary = "Resend user invitation",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> resendUserInvitation(
    @PathVariable(value = "id") Integer id)
  {

      final var command = new ResendUserInvitationCommand(id);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @PutMapping(
   value = "users/{id}/status"
  )
  @Operation(
    summary = "Update user status",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPUserDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<IGRPUserDTO> updateUserStatus(
    @RequestParam(value = "value") String value, @PathVariable(value = "id") Integer id)
  {

      final var command = new UpdateUserStatusCommand(value, id);

      return commandBus.send(command);

  }

   @GetMapping(
   value = "users/me/applications"
  )
  @Operation(
    summary = "Get current user applications",
    description = "Get current user applications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getCurrentUserApplications(
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "applicationName", required = false) String applicationName)
  {

      final var query = new GetCurrentUserApplicationsQuery(applicationCode, applicationName);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Get current user application menus",
    description = "Get current user application menus",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<MenuEntryDTO>> getCurrentUserApplicationMenus(
    @RequestParam(value = "menuCode", required = false) String menuCode, @PathVariable(value = "applicationCode") String applicationCode)
  {

      final var query = new GetCurrentUserApplicationMenusQuery(menuCode, applicationCode);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/departments"
  )
  @Operation(
    summary = "Get current user departments",
    description = "Get current user departments",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<DepartmentDTO>> getCurrentUserDepartments(
    @RequestParam(value = "departmentCode", required = false) String departmentCode)
  {

      final var query = new GetCurrentUserDepartmentsQuery(departmentCode);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/departments/{departmentCode}/roles"
  )
  @Operation(
    summary = "Get current user department roles",
    description = "Get current user department roles",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getCurrentUserDepartmentRoles(
    @RequestParam(value = "roleCode", required = false) String roleCode, @PathVariable(value = "departmentCode") String departmentCode)
  {

      final var query = new GetCurrentUserDepartmentRolesQuery(roleCode, departmentCode);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/permissions"
  )
  @Operation(
    summary = "Get current user permissions",
    description = "Get current user permissions",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionDTO>> getCurrentUserPermissions(
    @RequestParam(value = "roleCode", required = false) String roleCode)
  {

      final var query = new GetCurrentUserPermissionsQuery(roleCode);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/roles"
  )
  @Operation(
    summary = "Get current user roles",
    description = "Get current user roles",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getCurrentUserRoles(
    @RequestParam(value = "departmentCode", required = false) String departmentCode)
  {

      final var query = new GetCurrentUserRolesQuery(departmentCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/applications"
  )
  @Operation(
    summary = "Get user applications",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getUserApplications(
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "applicationName", required = false) String applicationName, @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserApplicationsQuery(applicationCode, applicationName, id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Get user application menus",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<MenuEntryDTO>> getUserApplicationMenus(
    @RequestParam(value = "menuCode", required = false) String menuCode, @PathVariable(value = "id") Integer id,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var query = new GetUserApplicationMenusQuery(menuCode, id, applicationCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/departments"
  )
  @Operation(
    summary = "Get user departments",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<DepartmentDTO>> getUserDepartments(
    @RequestParam(value = "departmentCode", required = false) String departmentCode, @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserDepartmentsQuery(departmentCode, id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_VIEW)")
   @GetMapping(
   value = "users/{id}/departments/{departmentCode}/roles"
  )
  @Operation(
    summary = "Get user department roles",
    description = "This Permission is required: igrp.users.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<RoleDTO>> getUserDepartmentRoles(
    @RequestParam(value = "roleCode", required = false) String roleCode, @PathVariable(value = "id") Integer id,@PathVariable(value = "departmentCode") String departmentCode)
  {

      final var query = new GetUserDepartmentRolesQuery(roleCode, id, departmentCode);

      return queryBus.handle(query);

  }

   @PostMapping(
   value = "users/me/applications/recent/{applicationCode}"
  )
  @Operation(
    summary = "Register access history",
    description = "Register access history",
    responses = {
      @ApiResponse(
          responseCode = "204",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> registerAccessHistory(
    @PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new RegisterAccessHistoryCommand(applicationCode);

      return commandBus.send(command);

  }

   @GetMapping(
   value = "users/me/applications/recent"
  )
  @Operation(
    summary = "Get recent applications",
    description = "Get recent applications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getRecentApplications(
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "applicationName", required = false) String applicationName,
    @RequestParam(value = "max", required = false) Integer max)
  {

      final var query = new GetRecentApplicationsQuery(applicationCode, applicationName, max);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/applications/favorites"
  )
  @Operation(
    summary = "Get favorite applications",
    description = "Get favorite applications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getFavoriteApplications(
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "applicationName", required = false) String applicationName)
  {

      final var query = new GetFavoriteApplicationsQuery(applicationCode, applicationName);

      return queryBus.handle(query);

  }

   @PostMapping(
   value = "users/me/applications/favorites/{applicationCode}"
  )
  @Operation(
    summary = "Add favorite application",
    description = "Add favorite application",
    responses = {
      @ApiResponse(
          responseCode = "204",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> addFavoriteApplication(
    @PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new AddFavoriteApplicationCommand(applicationCode);

      return commandBus.send(command);

  }

   @DeleteMapping(
   value = "users/me/applications/favorites/{applicationCode}"
  )
  @Operation(
    summary = "Remove favorite application",
    description = "Remove favorite application",
    responses = {
      @ApiResponse(
          responseCode = "204",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> removeFavoriteApplication(
    @PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new RemoveFavoriteApplicationCommand(applicationCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @GetMapping(
   value = "users/invite"
  )
  @Operation(
    summary = "Get user invitations",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<InvitationDTO>> getUserInvitations(
    @RequestParam(value = "identifierValue", required = false) String identifierValue)
  {

      final var query = new GetUserInvitationsQuery(identifierValue);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/invite/{id}"
  )
  @Operation(
    summary = "Get user invitation",
    description = "Get user invitation",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> getUserInvitation(
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetUserInvitationQuery(id);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/invite/by-token/{token}"
  )
  @Operation(
    summary = "Get user invitation by token",
    description = "Get user invitation by token",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = InvitationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<InvitationDTO> getUserInvitationByToken(
    @PathVariable(value = "token") String token)
  {

      final var query = new GetUserInvitationByTokenQuery(token);

      return queryBus.handle(query);

  }

   @GetMapping(
   value = "users/me/roles/active"
  )
  @Operation(
    summary = "Get active current user role",
    description = "Get active current user role",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDepartmentDTO> getActiveCurrentUserRole(
    )
  {

      final var query = new GetActiveCurrentUserRoleQuery();

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @GetMapping(
   value = "users/{id}/roles/active"
  )
  @Operation(
    summary = "Get active user role",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDepartmentDTO> getActiveUserRole(
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetActiveUserRoleQuery(id);

      return queryBus.handle(query);

  }

   @PostMapping(
   value = "users/me/roles/active"
  )
  @Operation(
    summary = "Set active current user role",
    description = "Set active current user role",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDepartmentDTO> setActiveCurrentUserRole(@Valid @RequestBody RoleDepartmentDTO setActiveCurrentUserRoleRequest
    )
  {

      final var command = new SetActiveCurrentUserRoleCommand(setActiveCurrentUserRoleRequest);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_USERS_MANAGE)")
   @PostMapping(
   value = "users/{id}/roles/active"
  )
  @Operation(
    summary = "Set active user role",
    description = "This Permission is required: igrp.users.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDepartmentDTO> setActiveUserRole(@Valid @RequestBody RoleDepartmentDTO setActiveUserRoleRequest
    , @PathVariable(value = "id") Integer id)
  {

      final var command = new SetActiveUserRoleCommand(setActiveUserRoleRequest, id);

      return commandBus.send(command);

  }

}