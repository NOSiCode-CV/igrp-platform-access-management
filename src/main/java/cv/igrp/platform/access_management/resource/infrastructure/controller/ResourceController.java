package cv.igrp.platform.access_management.resource.infrastructure.controller;

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
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.queries.queries.*;


import java.util.List;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Resource", description = "Resource Management")
public class ResourceController {

  
  private final CommandBus commandBus;
  private final QueryBus queryBus;

  
  public ResourceController(
    CommandBus commandBus, QueryBus queryBus
  ) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping(
    value = "resources"
  )
  @Operation(
    summary = "GET method to handle operations for getResources",
    description = "GET method to handle operations for getResources",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "A List Resources",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ResourceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ResourceDTO>> getResources(
    )
  {
      final var query = new GetResourcesQuery();
      ResponseEntity<List<ResourceDTO>> response = (ResponseEntity<List<ResourceDTO>>) queryBus.handle(query);
      return ResponseEntity.ok(response.getBody());
      //return queryBus.handle(query);
  }

}