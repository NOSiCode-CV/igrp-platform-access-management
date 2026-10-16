/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

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
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.m2m.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPBusinessUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.application.queries.GetGlobalConfigurationQuery;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(
    name = "M2m",
    description = "Machine-to-Machine"
)
public class M2MController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public M2MController(CommandBus commandBus, QueryBus queryBus) {

          this.commandBus = commandBus;
          this.queryBus = queryBus;
  }

  /**
   * Read-only view of a global configuration for machine callers.
   *
   * <p>Exists so a framework application can load the organization Brand with
   * its own {@code client_credentials} token. The user-scoped
   * {@code GET /api/global-configuration} cannot serve this: public pages
   * (login, invitation) have no user, so a freshly started app process renders
   * default colours and logos there until some signed-in request warms its
   * cache.
   *
   * <p>Adding the user-scoped path to {@code M2MTokenRejectionFilter}'s skip
   * list was the alternative and is unsafe — that filter exists precisely
   * because a sid-less M2M token reaching user-scoped code blows up in the
   * permission cache on {@code Integer.parseInt(jwt.getSubject())}. A separate
   * endpoint under {@code /api/m2m/} keeps machine tokens out of the user path,
   * and is already covered by the existing skip prefix.
   *
   * <p>Authorized like every other {@code /api/m2m/} endpoint: being a
   * registered M2M client is enough. No permission is checked, because the
   * payload is branding — colours and public logo URLs — and is not secret.
   * Writes are unaffected: {@code POST /api/global-configuration} still
   * requires a user token and {@code igrp.applications.customize}.
   */
  @GetMapping(
  value = "m2m/global-configuration"
  )
  @Operation(
    summary = "Get global configuration (M2M)",
    description = "Reads a global configuration with a client_credentials token. Returns the most "
            + "recently modified row for the given type, identical to GET /api/global-configuration. "
            + "Intended for branding on pages that have no signed-in user.",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = GlobalConfigurationDTO.class,
                  type = "object")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Unknown configuration type", content = @Content),
      @ApiResponse(responseCode = "404", description = "No configuration saved for this type", content = @Content)
    }
  )
  public ResponseEntity<GlobalConfigurationDTO> getGlobalConfigurationForMachine(
    @RequestParam(value = "type") String type)
  {

      final var query = new GetGlobalConfigurationQuery(type);

      return queryBus.handle(query);

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
    , @PathVariable(value = "code") String code
    , @RequestParam(value = "syncRoles", defaultValue = "true") boolean syncRoles)
  {

      final var command = new SyncApplicationMenusCommand(syncApplicationMenusRequest, code, syncRoles);

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
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = IGRPBusinessUserDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<IGRPBusinessUserDTO>> getUsersForBusiness(@RequestBody List<String> getUsersForBusinessRequest
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