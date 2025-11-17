/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.menu.interfaces.rest;

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
import cv.igrp.platform.access_management.menu.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.menu.application.commands.*;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Menu", description = "Menu Management")
public class MenuController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public MenuController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
   @GetMapping(
   value = "menus"
  )
  @Operation(
    summary = "GET method to handle operations for getMenus",
    description = "GET method to handle operations for getMenus",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The List of the Menus",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<MenuEntryDTO>> getMenus(
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "type", required = false) String type,
    @RequestParam(value = "status", required = false) String status,
    @RequestParam(value = "applicationCode", required = false) String applicationCode,
    @RequestParam(value = "code", required = false) String code,
    @RequestParam(value = "departmentCode", required = false) String departmentCode)
  {

      final var query = new GetMenusQuery(name, type, status, applicationCode, code, departmentCode);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      return response;
  }

   @GetMapping(
   value = "menus/{code}"
  )
  @Operation(
    summary = "GET method to handle operations for getMenuById",
    description = "GET method to handle operations for getMenuById",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The Menu Data",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> getMenuById(
    @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetMenuByIdQuery(applicationCode, code);

      ResponseEntity<MenuEntryDTO> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "menus"
  )
  @Operation(
    summary = "POST method to handle operations for createMenu",
    description = "POST method to handle operations for createMenu",
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
    )
  {

      final var command = new CreateMenuCommand(createMenuRequest);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PutMapping(
   value = "menus/{code}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateMenu",
    description = "PUT method to handle operations for updateMenu",
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
    , @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new UpdateMenuCommand(updateMenuRequest, applicationCode, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "menus/{code}"
  )
  @Operation(
    summary = "DELETE method to handle operations for deleteMenu",
    description = "DELETE method to handle operations for deleteMenu",
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
    @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new DeleteMenuCommand(applicationCode, code);

       ResponseEntity<String> response = commandBus.send(command);

       return response;
  }

   @GetMapping(
   value = "menus/app/{appCode}"
  )
  @Operation(
    summary = "GET method to handle operations for getAppMenus",
    description = "GET method to handle operations for getAppMenus",
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
  
  public ResponseEntity<List<MenuEntryDTO>> getAppMenus(
    @PathVariable(value = "appCode") String appCode)
  {

      final var query = new GetAppMenusQuery(appCode);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      return response;
  }

   @PostMapping(
   value = "menus/{code}/roles"
  )
  @Operation(
    summary = "POST method to handle operations for addPermissionsToMenu",
    description = "POST method to handle operations for addPermissionsToMenu",
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
  
  public ResponseEntity<MenuEntryDTO> addPermissionsToMenu(@RequestBody List<String> addPermissionsToMenuRequest
    , @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new AddPermissionsToMenuCommand(addPermissionsToMenuRequest, applicationCode, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "menus/{code}/roles"
  )
  @Operation(
    summary = "DELETE method to handle operations for removePermissionsFromMenu",
    description = "DELETE method to handle operations for removePermissionsFromMenu",
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
  
  public ResponseEntity<MenuEntryDTO> removePermissionsFromMenu(@RequestBody List<String> removePermissionsFromMenuRequest
    , @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new RemovePermissionsFromMenuCommand(removePermissionsFromMenuRequest, applicationCode, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @PostMapping(
   value = "menus/{code}/departments"
  )
  @Operation(
    summary = "POST method to handle operations for addDepartmentsToMenu",
    description = "POST method to handle operations for addDepartmentsToMenu",
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
  
  public ResponseEntity<MenuEntryDTO> addDepartmentsToMenu(@RequestBody List<String> addDepartmentsToMenuRequest
    , @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new AddDepartmentsToMenuCommand(addDepartmentsToMenuRequest, applicationCode, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

   @DeleteMapping(
   value = "menus/{code}/departments"
  )
  @Operation(
    summary = "DELETE method to handle operations for removeDepartmentsFromMenu",
    description = "DELETE method to handle operations for removeDepartmentsFromMenu",
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
  
  public ResponseEntity<MenuEntryDTO> removeDepartmentsFromMenu(@RequestBody List<String> removeDepartmentsFromMenuRequest
    , @RequestParam(value = "applicationCode") String applicationCode, @PathVariable(value = "code") String code)
  {

      final var command = new RemoveDepartmentsFromMenuCommand(removeDepartmentsFromMenuRequest, applicationCode, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       return response;
  }

}