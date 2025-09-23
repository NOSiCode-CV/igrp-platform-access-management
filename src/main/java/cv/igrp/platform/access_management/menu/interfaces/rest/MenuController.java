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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.menu.application.commands.*;
import cv.igrp.platform.access_management.menu.application.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Menu", description = "Menu Management")
public class MenuController {

  private static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public MenuController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
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
    @RequestParam(value = "code", required = false) String code)
  {

      LOGGER.debug("Operation started");

      final var query = new GetMenusQuery(name, type, status, applicationCode, code);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
    @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var query = new GetMenuByIdQuery(code);

      ResponseEntity<MenuEntryDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var command = new CreateMenuCommand(createMenuRequest);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
    , @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var command = new UpdateMenuCommand(updateMenuRequest, code);

       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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
    @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var command = new DeleteMenuCommand(code);

       ResponseEntity<String> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetAppMenusQuery(appCode);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "menus/{code}/addPermissions"
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
                  implementation = PermissionDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<PermissionDTO>> addPermissionsToMenu(@RequestBody List<String> addPermissionsToMenuRequest
    , @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var command = new AddPermissionsToMenuCommand(addPermissionsToMenuRequest, code);

       ResponseEntity<List<PermissionDTO>> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @PostMapping(
    value = "menus/{code}/removePermissions"
  )
  @Operation(
    summary = "POST method to handle operations for removePermissionsFromMenu",
    description = "POST method to handle operations for removePermissionsFromMenu",
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
  
  public ResponseEntity<List<PermissionDTO>> removePermissionsFromMenu(@RequestBody List<String> removePermissionsFromMenuRequest
    , @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var command = new RemovePermissionsFromMenuCommand(removePermissionsFromMenuRequest, code);

       ResponseEntity<List<PermissionDTO>> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}