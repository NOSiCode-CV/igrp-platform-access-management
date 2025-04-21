package cv.igrp.platform.access_management.department.infrastructure.controller;

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
import cv.igrp.platform.access_management.department.application.commands.commands.*;
import cv.igrp.platform.access_management.department.application.queries.queries.*;


import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Department", description = "postDepartment")
public class DepartmentController {

  
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
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<String> postDepartment( @Valid @RequestBody DepartmentDTO postDepartmentRequest
    )
  {
      final var command = new PostDepartmentCommand(postDepartmentRequest);
       ResponseEntity<String> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<DepartmentDTO> getDepartments(
    )
  {
      final var query = new GetDepartmentsQuery();
      ResponseEntity<DepartmentDTO> response = queryBus.handle(query);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @PutMapping(
    value = "departments/{id}"
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
  
  public ResponseEntity<DepartmentDTO> updateDepartment( @Valid @RequestBody DepartmentDTO updateDepartmentRequest
    , @PathVariable(value = "id") Integer id)
  {
      final var command = new UpdateDepartmentCommand(updateDepartmentRequest, id);
       ResponseEntity<DepartmentDTO> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

  @DeleteMapping(
    value = "departments/{id}"
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
    @PathVariable(value = "id") Integer id)
  {
      final var command = new DeleteDepartmentCommand(id);
       ResponseEntity<?> response = commandBus.send(command);
       return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
  }

}