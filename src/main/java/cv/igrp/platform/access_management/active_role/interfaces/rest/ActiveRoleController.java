package cv.igrp.platform.access_management.active_role.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.active_role.application.commands.SwitchActiveRoleCommand;
import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO;
import cv.igrp.platform.access_management.active_role.application.dto.SwitchActiveRoleRequestDTO;
import cv.igrp.platform.access_management.active_role.application.queries.GetActiveRoleQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v3-05 (#13): the caller's active role, one per application. Any
 * authenticated user, for themselves; no extra permission.
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Active role", description = "The caller's active role per application")
public class ActiveRoleController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public ActiveRoleController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @GetMapping("users/me/role/active")
    @Operation(summary = "Get my active role in an application",
            description = "The stored choice when still valid, else the only valid role, else none "
                    + "(availableRoles lists the choices).",
            responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ActiveRoleDTO.class))))
    public ResponseEntity<ActiveRoleDTO> get(@RequestParam("applicationCode") String applicationCode) {
        return queryBus.handle(new GetActiveRoleQuery(applicationCode));
    }

    @PutMapping("users/me/role/active")
    @Operation(summary = "Switch my active role in an application",
            description = "The role must be valid for the caller in the application. Audited.",
            responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ActiveRoleDTO.class))))
    public ResponseEntity<ActiveRoleDTO> put(@RequestParam("applicationCode") String applicationCode,
                                             @Valid @RequestBody SwitchActiveRoleRequestDTO request) {
        return commandBus.send(new SwitchActiveRoleCommand(applicationCode, request));
    }
}
