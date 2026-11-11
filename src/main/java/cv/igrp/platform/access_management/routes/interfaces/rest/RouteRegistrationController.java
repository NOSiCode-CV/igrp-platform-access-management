package cv.igrp.platform.access_management.routes.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.routes.application.commands.RegisterRoutesCommand;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v3-02 (#10): services register their routes at startup with their own
 * client_credentials token. Guarded like the M2M sync: service accounts only,
 * with {@code igrp.m2m.sync} (see {@code ServiceAccountM2MAuthorizationFilter}).
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Routes", description = "Route registration by services")
public class RouteRegistrationController {

    private final CommandBus commandBus;

    public RouteRegistrationController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("routes/register")
    @Operation(
            summary = "Register a service's routes",
            description = "Creates a permission, with no assignments, for every key not seen before; leaves existing "
                    + "keys (name, description, assignments) unchanged. Service-account tokens only.",
            responses = @ApiResponse(responseCode = "200", description = "Counts of created and unchanged keys",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RouteRegistrationResultDTO.class))))
    public ResponseEntity<RouteRegistrationResultDTO> register(
            @Valid @RequestBody RouteRegistrationRequestDTO registration) {
        return commandBus.send(new RegisterRoutesCommand(registration));
    }
}
