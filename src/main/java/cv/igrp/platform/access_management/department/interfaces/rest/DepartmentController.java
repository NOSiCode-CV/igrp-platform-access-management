/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

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

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Department", description = "Department Management")
public class DepartmentController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public DepartmentController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PostMapping(
   value = "departments"
  )
  @Operation(
    summary = "POST method to handle operations for postDepartment",
    description = "POST method to handle operations for postDepartment",
    responses = {
      @ApiResponse(
          responseCode = "201",
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
  
  public ResponseEntity<DepartmentDTO> postDepartment(@Valid @RequestBody DepartmentDTO postDepartmentRequest
    )
  {

      final var command = new PostDepartmentCommand(postDepartmentRequest);

       ResponseEntity<DepartmentDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "departments"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartments",
    description = "GET method to handle operations for getDepartments",
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

      ResponseEntity<List<DepartmentDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "departments/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartmentById",
    description = "GET method to handle operations for getDepartmentById",
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

      ResponseEntity<DepartmentDTO> response = queryBus.handle(query);

      return response;
  }

   @PutMapping(
   value = "departments/{code}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateDepartment",
    description = "PUT method to handle operations for updateDepartment",
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

       ResponseEntity<DepartmentDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "departments/{code}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteDepartment",
    description = "DELETE method to handle operations for deleteDepartment",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "",
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

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "departments/by-code/{code}"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartmentByCode",
    description = "GET method to handle operations for getDepartmentByCode",
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
  
  public ResponseEntity<DepartmentDTO> getDepartmentByCode(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetDepartmentByCodeQuery(code);

      ResponseEntity<DepartmentDTO> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "departments/{code}/applications/available"
  )
  @Operation(
    summary = "GET method to handle operations for getAvailableApplicationsForDepartment",
    description = "GET method to handle operations for getAvailableApplicationsForDepartment",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus/available"
  )
  @Operation(
    summary = "GET method to handle operations for getMenusAvailableForDepartment",
    description = "GET method to handle operations for getMenusAvailableForDepartment",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "departments/{code}/resources/available"
  )
  @Operation(
    summary = "GET method to handle operations for getAvailableResourcesForDepartment",
    description = "GET method to handle operations for getAvailableResourcesForDepartment",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "departments/{code}/roles"
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
    , @PathVariable(value = "code") String code)
  {

      final var command = new CreateRoleCommand(createRoleRequest, code);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "departments/{code}/roles"
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
    @RequestParam(value = "roleCode", required = false) String roleCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetRolesQuery(roleCode, code);

      ResponseEntity<List<RoleDTO>> response = queryBus.handle(query);

      return response;
  }

   @PutMapping(
   value = "departments/{departmentCode}/roles/{roleCode}"
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
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new UpdateRoleCommand(updateRoleRequest, departmentCode, roleCode);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "departments/{departmentCode}/roles/{roleCode}"
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
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new DeleteRoleCommand(departmentCode, roleCode);

       ResponseEntity<Boolean> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
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
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new RemovePermissionsCommand(removePermissionsRequest, departmentCode, roleCode);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
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
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetPermissionsByRoleIdQuery(departmentCode, roleCode);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions"
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
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var command = new AddPermissionsCommand(addPermissionsRequest, departmentCode, roleCode);

       ResponseEntity<RoleDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "departments/{departmentCode}/roles/{roleCode}/permissions/available"
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
    @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "roleCode") String roleCode)
  {

      final var query = new GetAvailablePermissionsForRolesQuery(departmentCode, roleCode);

      ResponseEntity<List<PermissionDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "departments/{code}/applications"
  )
  @Operation(
    summary = "POST method to handle operations for addApplicationsToDepartment",
    description = "POST method to handle operations for addApplicationsToDepartment",
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
  
  public ResponseEntity<String> addApplicationsToDepartment(@RequestBody List<String> addApplicationsToDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddApplicationsToDepartmentCommand(addApplicationsToDepartmentRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "POST method to handle operations for addMenusToDepartment",
    description = "POST method to handle operations for addMenusToDepartment",
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
  
  public ResponseEntity<String> addMenusToDepartment(@RequestBody List<String> addMenusToDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new AddMenusToDepartmentCommand(addMenusToDepartmentRequest, departmentCode, applicationCode);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "departments/{code}/applications"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeApplicationsFromDepartment",
    description = "DELETE method to handle operations for removeApplicationsFromDepartment",
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
  
  public ResponseEntity<String> removeApplicationsFromDepartment(@RequestBody List<String> removeApplicationsFromDepartmentRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemoveApplicationsFromDepartmentCommand(removeApplicationsFromDepartmentRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeMenusFromDepartment",
    description = "DELETE method to handle operations for removeMenusFromDepartment",
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
  
  public ResponseEntity<String> removeMenusFromDepartment(@RequestBody List<String> removeMenusFromDepartmentRequest
    , @PathVariable(value = "departmentCode") String departmentCode,@PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new RemoveMenusFromDepartmentCommand(removeMenusFromDepartmentRequest, departmentCode, applicationCode);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "/departments/{code}/resources"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartmentResources",
    description = "GET method to handle operations for getDepartmentResources",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "/departments/{departmentCode}/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartmentMenus",
    description = "GET method to handle operations for getDepartmentMenus",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "/departments/{code}/applications"
  )
  @Operation(
    summary = "GET method to handle operations for getDepartmentApplications",
    description = "GET method to handle operations for getDepartmentApplications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
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

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      return response;
  }

}