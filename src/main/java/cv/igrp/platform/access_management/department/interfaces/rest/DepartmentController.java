/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package cv.igrp.platform.access_management.department.interfaces.rest;

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
import cv.igrp.platform.access_management.department.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.department.application.commands.*;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleChildHierarchyDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleParentHierarchyDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(
    name = "Department",
    description = "Department Management"
)
public class DepartmentController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public DepartmentController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_CREATE)")
   @PostMapping(
   value = "departments"
  )
  @Operation(
    summary = "Post department",
    description = "This Permission is required: igrp.department.create",
    responses = {
      @ApiResponse(
          responseCode = "201",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<DepartmentDTO> postDepartment(@Valid @RequestBody DepartmentDTO postDepartmentRequest
    )
  {

      final var command = new PostDepartmentCommand(postDepartmentRequest);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_LIST)")
   @GetMapping(
   value = "departments"
  )
  @Operation(
    summary = "Get departments",
    description = "This Permission is required: igrp.department.list",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List of Departments",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<List<DepartmentDTO>> getDepartments(
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "status", required = false) String status,
    @RequestParam(value = "code", required = false) String code,
    @RequestParam(value = "parentCode", required = false) String parentCode)
  {

      final var query = new GetDepartmentsQuery(name, status, code, parentCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{id}"
  )
  @Operation(
    summary = "Get department by id",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Department Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<DepartmentDTO> getDepartmentById(
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetDepartmentByIdQuery(id);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_UPDATE)")
   @PutMapping(
   value = "departments/{code}"
  )
  @Operation(
    summary = "Update department",
    description = "This Permission is required: igrp.department.update",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Updated Department",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = DepartmentDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<DepartmentDTO> updateDepartment(@Valid @RequestBody DepartmentDTO updateDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new UpdateDepartmentCommand(updateDepartmentRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_DELETE)")
   @DeleteMapping(
   value = "departments/{code}"
  )
  @Operation(
    summary = "Delete department",
    description = "This Permission is required: igrp.department.delete",
    responses = {
      @ApiResponse(
          responseCode = "204",
          
          content = @Content(
              mediaType = "",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<?> deleteDepartment(
    @PathVariable(value = "code") String code)
  {

      final var command = new DeleteDepartmentCommand(code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/by-code/{code}"
  )
  @Operation(
    summary = "Get department by code",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<DepartmentDTO> getDepartmentByCode(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetDepartmentByCodeQuery(code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @GetMapping(
   value = "departments/{code}/applications/available"
  )
  @Operation(
    summary = "Get available applications for department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<List<ApplicationDTO>> getAvailableApplicationsForDepartment(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetAvailableApplicationsForDepartmentQuery(code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @GetMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus/available"
  )
  @Operation(
    summary = "Get menus available for department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<List<MenuEntryDTO>> getMenusAvailableForDepartment(
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var query = new GetMenusAvailableForDepartmentQuery(departmentCode, applicationCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @GetMapping(
   value = "departments/{code}/resources/available"
  )
  @Operation(
    summary = "Get available resources for department",
    description = "This Permission is required: igrp.department.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ResourceDTO>> getAvailableResourcesForDepartment(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetAvailableResourcesForDepartmentQuery(code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{code}/roles"
  )
  @Operation(
    summary = "Create role",
    description = "This Permission is required: igrp.department.manage",
    responses = {
      @ApiResponse(
          responseCode = "201",
          
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
    , @PathVariable(value = "code") String code)
  {

      final var command = new CreateRoleCommand(createRoleRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{code}/roles"
  )
  @Operation(
    summary = "Get roles",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<List<RoleDTO>> getRoles(
    @RequestParam(value = "roleCode", required = false) String roleCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetRolesQuery(roleCode, code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PutMapping(
   value = "departments/{departmentCode}/roles/{roleCode}"
  )
  @Operation(
    summary = "Update role",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<RoleDTO> updateRole(@Valid @RequestBody RoleDTO updateRoleRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new UpdateRoleCommand(updateRoleRequest, departmentCode, roleCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{departmentCode}/roles/{roleCode}"
  )
  @Operation(
    summary = "Delete role",
    description = "This Permission is required: igrp.department.manage",
    responses = {
      @ApiResponse(
          responseCode = "204",
          
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
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new DeleteRoleCommand(departmentCode, roleCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
  )
  @Operation(
    summary = "Remove permissions",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<RoleDTO> removePermissions(@RequestBody List<String> removePermissionsRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new RemovePermissionsCommand(removePermissionsRequest, departmentCode, roleCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
  )
  @Operation(
    summary = "Get permissions by role id",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<List<PermissionDTO>> getPermissionsByRoleId(
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetPermissionsByRoleIdQuery(departmentCode, roleCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
  )
  @Operation(
    summary = "Add permissions",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<RoleDTO> addPermissions(@RequestBody List<String> addPermissionsRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new AddPermissionsCommand(addPermissionsRequest, departmentCode, roleCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions/available"
  )
  @Operation(
    summary = "Get available permissions for roles",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<List<PermissionDTO>> getAvailablePermissionsForRoles(
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetAvailablePermissionsForRolesQuery(departmentCode, roleCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{code}/applications"
  )
  @Operation(
    summary = "Add applications to department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> addApplicationsToDepartment(@RequestBody List<String> addApplicationsToDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddApplicationsToDepartmentCommand(addApplicationsToDepartmentRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Add menus to department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> addMenusToDepartment(@RequestBody List<String> addMenusToDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new AddMenusToDepartmentCommand(addMenusToDepartmentRequest, departmentCode, applicationCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{code}/applications"
  )
  @Operation(
    summary = "Remove applications from department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> removeApplicationsFromDepartment(@RequestBody List<String> removeApplicationsFromDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemoveApplicationsFromDepartmentCommand(removeApplicationsFromDepartmentRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Remove menus from department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> removeMenusFromDepartment(@RequestBody List<String> removeMenusFromDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new RemoveMenusFromDepartmentCommand(removeMenusFromDepartmentRequest, departmentCode, applicationCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "/departments/{code}/resources"
  )
  @Operation(
    summary = "Get department resources",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ResourceDTO>> getDepartmentResources(
    @RequestParam(value = "resourceName", required = false) String resourceName, @PathVariable(value = "code") String code)
  {

      final var query = new GetDepartmentResourcesQuery(resourceName, code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "/departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Get department menus",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<List<MenuEntryDTO>> getDepartmentMenus(
    @RequestParam(value = "menuCode", required = false) String menuCode, @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var query = new GetDepartmentMenusQuery(menuCode, departmentCode, applicationCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "/departments/{code}/applications"
  )
  @Operation(
    summary = "Get department applications",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<List<ApplicationDTO>> getDepartmentApplications(
    @RequestParam(value = "applicationCode", required = false) String applicationCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetDepartmentApplicationsQuery(applicationCode, code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{departmentCode}/resources"
  )
  @Operation(
    summary = "Add resources to department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> addResourcesToDepartment(@RequestBody List<String> addResourcesToDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode)
  {

      final var command = new AddResourcesToDepartmentCommand(addResourcesToDepartmentRequest, departmentCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{departmentCode}/resources"
  )
  @Operation(
    summary = "Remove resources from department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> removeResourcesFromDepartment(@RequestBody List<String> removeResourcesFromDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode)
  {

      final var command = new RemoveResourcesFromDepartmentCommand(removeResourcesFromDepartmentRequest, departmentCode);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{code}/permissions"
  )
  @Operation(
    summary = "Get department permissions",
    description = "This Permission is required: igrp.department.view",
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
  
  public ResponseEntity<List<PermissionDTO>> getDepartmentPermissions(
    @RequestParam(value = "permissionName", required = false) String permissionName, @PathVariable(value = "code") String code)
  {

      final var query = new GetDepartmentPermissionsQuery(permissionName, code);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @PostMapping(
   value = "departments/{code}/permissions"
  )
  @Operation(
    summary = "Add permissions to department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> addPermissionsToDepartment(@RequestBody List<String> addPermissionsToDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddPermissionsToDepartmentCommand(addPermissionsToDepartmentRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @DeleteMapping(
   value = "departments/{code}/permissions"
  )
  @Operation(
    summary = "Remove permissions from department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<String> removePermissionsFromDepartment(@RequestBody List<String> removePermissionsFromDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemovePermissionsFromDepartmentCommand(removePermissionsFromDepartmentRequest, code);

      return commandBus.send(command);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_MANAGE)")
   @GetMapping(
   value = "departments/{departmentCode}/permissions/available"
  )
  @Operation(
    summary = "Get available permissions for department",
    description = "This Permission is required: igrp.department.manage",
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
  
  public ResponseEntity<List<PermissionDTO>> getAvailablePermissionsForDepartment(
    @RequestParam(value = "resourceName", required = false) String resourceName, @PathVariable(value = "departmentCode") String departmentCode)
  {

      final var query = new GetAvailablePermissionsForDepartmentQuery(resourceName, departmentCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/children"
  )
  @Operation(
    summary = "Get role children",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleChildHierarchyDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleChildHierarchyDTO> getRoleChildren(
    @RequestParam(value = "level", required = false) Integer level, @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetRoleChildrenQuery(level, departmentCode, roleCode);

      return queryBus.handle(query);

  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/parents"
  )
  @Operation(
    summary = "Get role parents",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleParentHierarchyDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleParentHierarchyDTO> getRoleParents(
    @RequestParam(value = "level", required = false) Integer level, @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetRoleParentsQuery(level, departmentCode, roleCode);

      return queryBus.handle(query);

  }

}