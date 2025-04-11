package cv.igrp.platform.access_management.menu_entry.infrastructure.controller;

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
import cv.igrp.platform.access_management.menu_entry.application.commands.commands.*;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "MenuEntry", description = "Menu Entry Management")
public class MenuEntryController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public MenuEntryController(
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
          description = "The list of the Menus",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "MenuEntryDTO")
          )
      )
    }
  )
  
  public ResponseEntity<MenuEntryDTO> getMenus(
    @RequestParam(value = "applicationId") Integer applicationId)
  {
      final var query = new GetMenusQuery(applicationId);
      ResponseEntity<MenuEntryDTO> response = (ResponseEntity<MenuEntryDTO>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @GetMapping(
    value = "menus/{id}/{id}"
  )
  @Operation(
    summary = "GET method to handle operations for getMenuById",
    description = "GET method to handle operations for getMenuById",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = MenuEntryDTO.class,
                  type = "MenuEntryDTO")
          )
      )
    }
  )
  
  public ResponseEntity<List<MenuEntryDTO>> getMenuById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetMenuByIdQuery(id);
      ResponseEntity<List<MenuEntryDTO>> response = (ResponseEntity<List<MenuEntryDTO>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @GetMapping(
    value = "menus"
  )
  @Operation(
    summary = "GET method to handle operations for createMenu",
    description = "GET method to handle operations for createMenu",
    responses = {
      @ApiResponse(
          responseCode = "201",
          description = "The persisted Menu Entry Data",
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
      final var query = new CreateMenuQuery(createMenuRequest);
      ResponseEntity<MenuEntryDTO> response = (ResponseEntity<MenuEntryDTO>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

  @PutMapping(
    value = "menus/{id}/{id}"
  )
  @Operation(
    summary = "PUT method to handle operations for updateMenu",
    description = "PUT method to handle operations for updateMenu",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "The updated Menu Entry",
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
       ResponseEntity<MenuEntryDTO> response = (ResponseEntity<MenuEntryDTO>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

  @DeleteMapping(
    value = "menu/{id}/{id}"
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
       ResponseEntity<String> response = (ResponseEntity<String>) commandBus.send(command);
       return ResponseEntity.ok(response.getBody());
       //return commandBus.send(command);
  }

}