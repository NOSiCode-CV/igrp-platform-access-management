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
   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_CREATE)")
   @PostMapping(
   value = "applications"
  )
  @Operation(
    summary = "Create application",
    description = "This Permission is required: igrp.application.create",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_LIST)")
   @GetMapping(
   value = "applications"
  )
  @Operation(
    summary = "Get applications",
    description = "This Permission is required: igrp.application.list",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_VIEW)")
   @GetMapping(
   value = "applications/{id}"
  )
  @Operation(
    summary = "Get application by id",
    description = "This Permission is required: igrp.application.view",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_UPDATE)")
   @PutMapping(
   value = "applications/{code}"
  )
  @Operation(
    summary = "Update application",
    description = "This Permission is required: igrp.application.update",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_DELETE)")
   @DeleteMapping(
   value = "applications/{code}"
  )
  @Operation(
    summary = "Delete application",
    description = "This Permission is required: igrp.application.delete",
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

   @GetMapping(
   value = "applications/by-user/{uid}"
  )
  @Operation(
    summary = "Get applications by user",
    description = "Get applications by user",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_UPDATE)")
   @PostMapping(
   value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "Add application custom fields",
    description = "This Permission is required: igrp.application.update",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_UPDATE)")
   @DeleteMapping(
   value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "Remove application custom fields",
    description = "This Permission is required: igrp.application.update",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_VIEW)")
   @GetMapping(
   value = "/applications/{code}/custom-fields"
  )
  @Operation(
    summary = "Get application custom fields",
    description = "This Permission is required: igrp.application.view",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_VIEW)")
   @GetMapping(
   value = "/applications/by-code/{code}"
  )
  @Operation(
    summary = "Get application by code",
    description = "This Permission is required: igrp.application.view",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @PostMapping(
   value = "/applications/{applicationCode}/menus"
  )
  @Operation(
    summary = "Create menu",
    description = "This Permission is required: igrp.application.manage",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "The Persisted Menu",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> createMenu(@Valid @RequestBody MenuEntryDTO createMenuRequest
    , @PathVariable(value = "applicationCode") String applicationCode)
  {

      final var command = new CreateMenuCommand(createMenuRequest, applicationCode);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @PutMapping(
   value = "/applications/{applicationCode}/menus/{menuCode}"
  )
  @Operation(
    summary = "Update menu",
    description = "This Permission is required: igrp.application.manage",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Updated Menu",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> updateMenu(@Valid @RequestBody MenuEntryDTO updateMenuRequest
    , @PathVariable(value = "applicationCode") String applicationCode,@PathVariable(value = "menuCode") String menuCode)
  {

      final var command = new UpdateMenuCommand(updateMenuRequest, applicationCode, menuCode);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_DELETE)")
   @DeleteMapping(
   value = "/applications/{applicationCode}/menus/{menuCode}"
  )
  @Operation(
    summary = "Delete menu",
    description = "This Permission is required: igrp.application.delete",
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
  
  public ResponseEntity<String> deleteMenu(
    @PathVariable(value = "applicationCode") String applicationCode,@PathVariable(value = "menuCode") String menuCode)
  {

      final var command = new DeleteMenuCommand(applicationCode, menuCode);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_VIEW)")
   @GetMapping(
   value = "/applications/{code}/menus"
  )
  @Operation(
    summary = "Get application menus",
    description = "This Permission is required: igrp.application.view",
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

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @PostMapping(
   value = "/applications/{applicationCode}/menus/{menuCode}/roles"
  )
  @Operation(
    summary = "Add roles to menu",
    description = "This Permission is required: igrp.application.manage",
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
  
  public ResponseEntity<MenuEntryDTO> addRolesToMenu(@RequestBody List<String> addRolesToMenuRequest
    , @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "applicationCode") String applicationCode,@PathVariable(value = "menuCode") String menuCode)
  {

      final var command = new AddRolesToMenuCommand(addRolesToMenuRequest, departmentCode, applicationCode, menuCode);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @DeleteMapping(
   value = "/applications/{applicationCode}/menus/{menuCode}/roles"
  )
  @Operation(
    summary = "Remove roles from menu",
    description = "This Permission is required: igrp.application.manage",
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
  
  public ResponseEntity<MenuEntryDTO> removeRolesFromMenu(@RequestBody List<String> removeRolesFromMenuRequest
    , @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "applicationCode") String applicationCode,@PathVariable(value = "menuCode") String menuCode)
  {

      final var command = new RemoveRolesFromMenuCommand(removeRolesFromMenuRequest, departmentCode, applicationCode, menuCode);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @PostMapping(
   value = "applications/{code}/resources"
  )
  @Operation(
    summary = "Link resource to application",
    description = "This Permission is required: igrp.application.manage",
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
  
  public ResponseEntity<String> linkResourceToApplication(@RequestBody List<String> linkResourceToApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new LinkResourceToApplicationCommand(linkResourceToApplicationRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_APPLICATION_MANAGE)")
   @DeleteMapping(
   value = "applications/{code}/resources"
  )
  @Operation(
    summary = "Unlink resource from application",
    description = "This Permission is required: igrp.application.manage",
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
  
  public ResponseEntity<String> unlinkResourceFromApplication(@RequestBody List<String> unlinkResourceFromApplicationRequest
    , @PathVariable(value = "code") String code)
  {

      final var command = new UnlinkResourceFromApplicationCommand(unlinkResourceFromApplicationRequest, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

}