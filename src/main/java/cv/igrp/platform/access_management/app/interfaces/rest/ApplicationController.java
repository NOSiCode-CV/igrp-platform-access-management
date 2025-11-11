/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.app.interfaces.rest;

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
import cv.igrp.platform.access_management.app.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.app.application.commands.*;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import java.util.List;
import java.util.Map;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Application", description = "Application Management")
public class ApplicationController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public ApplicationController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @PostMapping(
    value = "applications"
  )
  @Operation(
    summary = "POST method to handle operations for createApplication",
    description = "POST method to handle operations for createApplication",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "The Persisted Application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> createApplication(@Valid @RequestBody ApplicationDTO createApplicationRequest
    )
  {

      final var command = new CreateApplicationCommand(createApplicationRequest);

       ResponseEntity<ApplicationDTO> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "applications"
  )
  @Operation(
    summary = "GET method to handle operations for getApplications",
    description = "GET method to handle operations for getApplications",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The List of Application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getApplications(
    @RequestParam(value = "code", required = false) String code,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "slug", required = false) String slug,
    @RequestParam(value = "departmentCode", required = false) String departmentCode,
    @RequestParam(value = "type", required = false) String type)
  {

      final var query = new GetApplicationsQuery(code, name, slug, departmentCode, type);

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "applications/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationById",
    description = "GET method to handle operations for getApplicationById",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Application Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> getApplicationById(
    @PathVariable(value = "id") Integer id)
  {

      final var query = new GetApplicationByIdQuery(id);

      ResponseEntity<ApplicationDTO> response = queryBus.handle(query);

      return response;
  }

   @PutMapping(
    value = "applications/{code}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateApplication",
    description = "PUT method to handle operations for updateApplication",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Updated Application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<ApplicationDTO> updateApplication(@Valid @RequestBody ApplicationDTO updateApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new UpdateApplicationCommand(updateApplicationRequest, code);

       ResponseEntity<ApplicationDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "applications/{code}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteApplication",
    description = "DELETE method to handle operations for deleteApplication",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No content",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> deleteApplication(
    @PathVariable(value = "code") String code)
  {

      final var command = new DeleteApplicationCommand(code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
    value = "applications/by-ids"
  )
  @Operation(
    summary = "POST method to handle operations for getApplicationsByIds",
    description = "POST method to handle operations for getApplicationsByIds",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The List of Application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getApplicationsByIds(@RequestBody List<Integer> getApplicationsByIdsRequest
    )
  {

      final var command = new GetApplicationsByIdsCommand(getApplicationsByIdsRequest);

       ResponseEntity<List<ApplicationDTO>> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "applications/by-user/{uid}"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationsByUser",
    description = "GET method to handle operations for getApplicationsByUser",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List of accessible Applications",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getApplicationsByUser(
    @PathVariable(value = "uid") String uid)
  {

      final var query = new GetApplicationsByUserQuery(uid);

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "/applications/denied-to-user/{uid}"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationDeniedToUser",
    description = "GET method to handle operations for getApplicationDeniedToUser",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List of Applications denied to User",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ApplicationDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<List<ApplicationDTO>> getApplicationDeniedToUser(
    @PathVariable(value = "uid") String uid)
  {

      final var query = new GetApplicationDeniedToUserQuery(uid);

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "POST method to handle operations for addApplicationCustomFields",
    description = "POST method to handle operations for addApplicationCustomFields",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No Content",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> addApplicationCustomFields(@RequestBody Map<String, ?> addApplicationCustomFieldsRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddApplicationCustomFieldsCommand(addApplicationCustomFieldsRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeApplicationCustomFields",
    description = "DELETE method to handle operations for removeApplicationCustomFields",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No Content",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> removeApplicationCustomFields(@RequestBody List<String> removeApplicationCustomFieldsRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemoveApplicationCustomFieldsCommand(removeApplicationCustomFieldsRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
    value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationCustomFields",
    description = "GET method to handle operations for getApplicationCustomFields",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Application Custom Fields ",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<Map<String, ?>> getApplicationCustomFields(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetApplicationCustomFieldsQuery(code);

      ResponseEntity<Map<String, ?>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "/applications/by-code/{code}"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationByCode",
    description = "GET method to handle operations for getApplicationByCode",
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
  
  public ResponseEntity<ApplicationDTO> getApplicationByCode(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetApplicationByCodeQuery(code);

      ResponseEntity<ApplicationDTO> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
    value = "/applications/{code}/menus/available"
  )
  @Operation(
    summary = "GET method to handle operations for getApplicationMenus",
    description = "GET method to handle operations for getApplicationMenus",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Get all application menus",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<MenuEntryDTO>> getApplicationMenus(
    @PathVariable(value = "code") String code)
  {

      final var query = new GetApplicationMenusQuery(code);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
    value = "/applications/{code}/roles"
  )
  @Operation(
    summary = "POST method to handle operations for addRolesToApp",
    description = "POST method to handle operations for addRolesToApp",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Add roles to an application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> addRolesToApp(@Valid @RequestBody CodeListRequestDTO addRolesToAppRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddRolesToAppCommand(addRolesToAppRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "applications/{code}/roles"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeRoleFromApplication",
    description = "DELETE method to handle operations for removeRoleFromApplication",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "Remove a role from an application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> removeRoleFromApplication(@Valid @RequestBody CodeListRequestDTO removeRoleFromApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemoveRoleFromApplicationCommand(removeRoleFromApplicationRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
    value = "applications/{code}/departments"
  )
  @Operation(
    summary = "POST method to handle operations for addDepartmentsToApplication",
    description = "POST method to handle operations for addDepartmentsToApplication",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Add departments to an application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> addDepartmentsToApplication(@Valid @RequestBody CodeListRequestDTO addDepartmentsToApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new AddDepartmentsToApplicationCommand(addDepartmentsToApplicationRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
    value = "applications/{code}/departments"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeDepartmentFromApplication",
    description = "DELETE method to handle operations for removeDepartmentFromApplication",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "Remove a department from an application",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> removeDepartmentFromApplication(@Valid @RequestBody CodeListRequestDTO removeDepartmentFromApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new RemoveDepartmentFromApplicationCommand(removeDepartmentFromApplicationRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

}