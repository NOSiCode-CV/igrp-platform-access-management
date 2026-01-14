/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.m2m.interfaces.rest;

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
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.m2m.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "M2M", description = "Machine-to-Machine")
public class M2MController {

  
  private final CommandBus commandBus;

  public M2MController(CommandBus commandBus) {
          
          this.commandBus = commandBus;
  }
   @PostMapping(
   value = "m2m/sync/permissions"
  )
  @Operation(
    summary = "Sync permissions",
    description = "Sync permissions",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> syncPermissions(@Valid @RequestBody List<PermissionDTO> syncPermissionsRequest
    )
  {

      final var command = new SyncPermissionsCommand(syncPermissionsRequest);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/sync/resources"
  )
  @Operation(
    summary = "Sync resources",
    description = "Sync resources",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> syncResources(@Valid @RequestBody ResourceDTO syncResourcesRequest
    )
  {

      final var command = new SyncResourcesCommand(syncResourcesRequest);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/sync/applications"
  )
  @Operation(
    summary = "Sync applications",
    description = "Sync applications",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> syncApplications(@Valid @RequestBody ApplicationDTO syncApplicationsRequest
    )
  {

      final var command = new SyncApplicationsCommand(syncApplicationsRequest);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/sync/applications/{code}/menus"
  )
  @Operation(
    summary = "Sync application menus",
    description = "Sync application menus",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> syncApplicationMenus(@Valid @RequestBody List<MenuEntryDTO> syncApplicationMenusRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new SyncApplicationMenusCommand(syncApplicationMenusRequest, code);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/users"
  )
  @Operation(
    summary = "Get users for business",
    description = "Get users for business",
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
  
  public ResponseEntity<List<IGRPUserDTO>> getUsersForBusiness(@RequestBody List<String> getUsersForBusinessRequest
    , @RequestParam(value = "activeOnly", required = false) boolean activeOnly,
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "departmentCode", required = false) String departmentCode,
    @RequestParam(value = "roleCode", required = false) String roleCode,
    @RequestParam(value = "permissionName", required = false) String permissionName,
    @RequestParam(value = "includeChildrenDepartments", required = false) boolean includeChildrenDepartments,
    @RequestParam(value = "includeChildrenRoles", required = false) boolean includeChildrenRoles)
  {

      final var command = new GetUsersForBusinessCommand(getUsersForBusinessRequest, activeOnly, applicationCode, departmentCode, roleCode, permissionName, includeChildrenDepartments, includeChildrenRoles);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/departments"
  )
  @Operation(
    summary = "Get department for business",
    description = "Get department for business",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<DepartmentDTO>> getDepartmentForBusiness(@RequestBody List<String> getDepartmentForBusinessRequest
    , @RequestParam(value = "activeOnly", required = false) boolean activeOnly,
    @RequestParam(value = "parentCode", required = false) String parentCode,
    @RequestParam(value = "includeChildrenDepartments", required = false) boolean includeChildrenDepartments)
  {

      final var command = new GetDepartmentForBusinessCommand(getDepartmentForBusinessRequest, activeOnly, parentCode, includeChildrenDepartments);

      return commandBus.send(command);

  }

   @PostMapping(
   value = "m2m/roles"
  )
  @Operation(
    summary = "Get roles for business",
    description = "Get roles for business",
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
  
  public ResponseEntity<List<RoleDTO>> getRolesForBusiness(@RequestBody List<String> getRolesForBusinessRequest
    , @RequestParam(value = "activeOnly", required = false) boolean activeOnly,
    @RequestParam(value = "parentCode", required = false) String parentCode,
    @RequestParam(value = "includeChildrenRoles", required = false) boolean includeChildrenRoles)
  {

      final var command = new GetRolesForBusinessCommand(getRolesForBusinessRequest, activeOnly, parentCode, includeChildrenRoles);

      return commandBus.send(command);

  }

}