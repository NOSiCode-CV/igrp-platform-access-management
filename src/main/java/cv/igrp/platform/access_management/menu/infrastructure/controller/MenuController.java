package cv.igrp.platform.access_management.menu.infrastructure.controller;

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
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.menu.application.commands.commands.*;
import cv.igrp.platform.access_management.menu.application.queries.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Menu", description = "Menu Management")
public class MenuController {

  
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
    @RequestParam(value = "applicationId", required = false) Integer applicationId,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "type", required = false) String type)
  {
      final var query = new GetMenusQuery(applicationId, name, type);
      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @GetMapping(
    value = "menus/{id}"
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
                  type = "MenuEntryDTO")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> getMenuById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetMenuByIdQuery(id);
      ResponseEntity<MenuEntryDTO> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
                  type = "MenuEntryDTO")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> createMenu( @Valid @RequestBody MenuEntryDTO createMenuRequest
    )
  {
      final var command = new CreateMenuCommand(createMenuRequest);
       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PutMapping(
    value = "menus/{id}"
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
                  type = "MenuEntryDTO")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> updateMenu( @Valid @RequestBody MenuEntryDTO updateMenuRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdateMenuCommand(updateMenuRequest, id);
       ResponseEntity<MenuEntryDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @DeleteMapping(
    value = "menus/{id}"
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
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeleteMenuCommand(id);
       ResponseEntity<String> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

}