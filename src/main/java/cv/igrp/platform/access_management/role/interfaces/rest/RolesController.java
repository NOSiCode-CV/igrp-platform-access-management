/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.role.interfaces.rest;

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
import cv.igrp.platform.access_management.role.application.queries.*;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Roles", description = "Role Management")
public class RolesController {

  
  private final QueryBus queryBus;

  public RolesController(QueryBus queryBus) {
          this.queryBus = queryBus;
          
  }
   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "roles/by-code/{code}"
  )
  @Operation(
    summary = "Get roles by name",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDTO> getRolesByName(
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "code") String code)
  {

      final var query = new GetRolesByNameQuery(departmentCode, code);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      return response;
  }

   @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_DEPARTMENT_VIEW)")
   @GetMapping(
   value = "roles/{id}"
  )
  @Operation(
    summary = "Get role by id",
    description = "This Permission is required: igrp.department.view",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = RoleDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<RoleDTO> getRoleById(
    @RequestParam(value = "departmentCode") String departmentCode, @PathVariable(value = "id") Integer id)
  {

      final var query = new GetRoleByIdQuery(departmentCode, id);

      ResponseEntity<RoleDTO> response = queryBus.handle(query);

      return response;
  }

}