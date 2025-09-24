/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.app.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.app.application.commands.*;
import cv.igrp.platform.access_management.app.application.queries.*;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Application", description = "Application Management")
public class ApplicationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationController.class);


    private final CommandBus commandBus;
    private final QueryBus queryBus;


    public ApplicationController(
            CommandBus commandBus, QueryBus queryBus
    ) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping(
            value = "applications"
    )
    @Operation(
            summary = "POST method to handle operations for createApplication",
            description = "POST method to handle operations for createApplication",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "The Persisted Application",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<ApplicationDTO> createApplication(@Valid @RequestBody ApplicationDTO createApplicationRequest
    ) {

        LOGGER.debug("Operation started");

        final var command = new CreateApplicationCommand(createApplicationRequest);

        ResponseEntity<ApplicationDTO> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "applications"
    )
    @Operation(
            summary = "GET method to handle operations for getApplications",
            description = "GET method to handle operations for getApplications",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "The List of Application",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<List<ApplicationDTO>> getApplications(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "departmentCode", required = false) String departmentCode,
            @RequestParam(value = "type", required = false) String type) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationsQuery(code, name, slug, departmentCode, type);

        ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "applications/{id}"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationById",
            description = "GET method to handle operations for getApplicationById",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "The Application Data",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<ApplicationDTO> getApplicationById(
            @PathVariable(value = "id") Integer id) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationByIdQuery(id);

        ResponseEntity<ApplicationDTO> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @PutMapping(
            value = "applications/{code}"
    )
    @Operation(
            summary = "PUT method to handle operations for updateApplication",
            description = "PUT method to handle operations for updateApplication",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "The Updated Application",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<ApplicationDTO> updateApplication(@Valid @RequestBody ApplicationDTO updateApplicationRequest
            , @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var command = new UpdateApplicationCommand(updateApplicationRequest, code);

        ResponseEntity<ApplicationDTO> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @DeleteMapping(
            value = "applications/{code}"
    )
    @Operation(
            summary = "DELETE method to handle operations for deleteApplication",
            description = "DELETE method to handle operations for deleteApplication",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "No content",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = String.class,
                                            type = "String")
                            )
                    )
            }
    )

    public ResponseEntity<String> deleteApplication(
            @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var command = new DeleteApplicationCommand(code);

        ResponseEntity<String> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @PostMapping(
            value = "applications/by-ids"
    )
    @Operation(
            summary = "POST method to handle operations for getApplicationsByIds",
            description = "POST method to handle operations for getApplicationsByIds",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "The List of Application",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "object")
                            )
                    )
            }
    )

    public ResponseEntity<List<ApplicationDTO>> getApplicationsByIds(@RequestBody List<Integer> getApplicationsByIdsRequest
    ) {

        LOGGER.debug("Operation started");

        final var command = new GetApplicationsByIdsCommand(getApplicationsByIdsRequest);

        ResponseEntity<List<ApplicationDTO>> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "applications/by-user/{uid}"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationsByUser",
            description = "GET method to handle operations for getApplicationsByUser",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of accessible Applications",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<List<ApplicationDTO>> getApplicationsByUser(
            @PathVariable(value = "uid") String uid) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationsByUserQuery(uid);

        ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "/applications/denied-to-user/{uid}"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationDeniedToUser",
            description = "GET method to handle operations for getApplicationDeniedToUser",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of Applications denied to User",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = ApplicationDTO.class,
                                            type = "")
                            )
                    )
            }
    )

    public ResponseEntity<List<ApplicationDTO>> getApplicationDeniedToUser(
            @PathVariable(value = "uid") String uid) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationDeniedToUserQuery(uid);

        ResponseEntity<List<ApplicationDTO>> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @PostMapping(
            value = "/applications/{code}/custom-fields"
    )
    @Operation(
            summary = "POST method to handle operations for addApplicationCustomFields",
            description = "POST method to handle operations for addApplicationCustomFields",
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

    public ResponseEntity<String> addApplicationCustomFields(@RequestBody Map<String, ?> addApplicationCustomFieldsRequest
            , @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var command = new AddApplicationCustomFieldsCommand(addApplicationCustomFieldsRequest, code);

        ResponseEntity<String> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @PostMapping(
            value = "/applications/{code}/custom-fields/remove"
    )
    @Operation(
            summary = "POST method to handle operations for removeApplicationCustomFields",
            description = "POST method to handle operations for removeApplicationCustomFields",
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

    public ResponseEntity<String> removeApplicationCustomFields(@RequestBody List<String> removeApplicationCustomFieldsRequest
            , @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var command = new RemoveApplicationCustomFieldsCommand(removeApplicationCustomFieldsRequest, code);

        ResponseEntity<String> response = commandBus.send(command);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "/applications/{code}/custom-fields"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationCustomFields",
            description = "GET method to handle operations for getApplicationCustomFields",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Application Custom Fields ",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = String.class,
                                            type = "String")
                            )
                    )
            }
    )

    public ResponseEntity<Map<String, ?>> getApplicationCustomFields(
            @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationCustomFieldsQuery(code);

        ResponseEntity<Map<String, ?>> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "/applications/by-code/{code}"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationByCode",
            description = "GET method to handle operations for getApplicationByCode",
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

    public ResponseEntity<ApplicationDTO> getApplicationByCode(
            @PathVariable(value = "code") String code) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationByCodeQuery(code);

        ResponseEntity<ApplicationDTO> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping(
            value = "/applications/{id}/menus/available"
    )
    @Operation(
            summary = "GET method to handle operations for getApplicationMenus",
            description = "GET method to handle operations for getApplicationMenus",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Get all application menus",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = MenuEntryDTO.class,
                                            type = "object")
                            )
                    )
            }
    )

    public ResponseEntity<List<MenuEntryDTO>> getApplicationMenus(
            @PathVariable(value = "id") Integer id) {

        LOGGER.debug("Operation started");

        final var query = new GetApplicationMenusQuery(id);

        ResponseEntity<List<MenuEntryDTO>> response = queryBus.handle(query);

        LOGGER.debug("Operation finished");

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

}