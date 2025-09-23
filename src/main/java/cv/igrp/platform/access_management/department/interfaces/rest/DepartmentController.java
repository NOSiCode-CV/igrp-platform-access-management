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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.department.application.commands.*;
import cv.igrp.platform.access_management.department.application.queries.*;


import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Department", description = "postDepartment")
public class DepartmentController {

  private static final Logger LOGGER = LoggerFactory.getLogger(DepartmentController.class);

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public DepartmentController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping(
    value = "department"
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

      LOGGER.debug("Operation started");

      final var command = new PostDepartmentCommand(postDepartmentRequest);

       ResponseEntity<DepartmentDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetDepartmentsQuery(name, status, code, parentCode);

      ResponseEntity<List<DepartmentDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetDepartmentByIdQuery(id);

      ResponseEntity<DepartmentDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var command = new UpdateDepartmentCommand(updateDepartmentRequest, code);

       ResponseEntity<DepartmentDTO> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var command = new DeleteDepartmentCommand(code);

       ResponseEntity<?> response = commandBus.send(command);

       LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetDepartmentByCodeQuery(code);

      ResponseEntity<DepartmentDTO> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetAvailableApplicationsForDepartmentQuery(code);

      ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

  @GetMapping(
    value = "departments/{code}/menus/available"
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
    @PathVariable(value = "code") String code)
  {

      LOGGER.debug("Operation started");

      final var query = new GetMenusAvailableForDepartmentQuery(code);

      ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
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

      LOGGER.debug("Operation started");

      final var query = new GetAvailableResourcesForDepartmentQuery(code);

      ResponseEntity<List<ResourceDTO>> response = queryBus.handle(query);

      LOGGER.debug("Operation finished");

      return ResponseEntity.status(response.getStatusCode())
              .headers(response.getHeaders())
              .body(response.getBody());
  }

}