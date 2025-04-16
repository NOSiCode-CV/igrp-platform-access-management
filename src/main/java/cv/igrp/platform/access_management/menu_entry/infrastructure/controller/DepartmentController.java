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




@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Department", description = "Department")
public class DepartmentController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public DepartmentController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping(
    value = "department/{id}/{id}"
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
                  type = "DepartmentDTO")
          )
      )
    }
  )
  
  public ResponseEntity<Department> getDepartmentById(
    @PathVariable(value = "id") Integer id)
  {
      final var query = new GetDepartmentByIdQuery(id);
      ResponseEntity<Department> response = (ResponseEntity<Department>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

}