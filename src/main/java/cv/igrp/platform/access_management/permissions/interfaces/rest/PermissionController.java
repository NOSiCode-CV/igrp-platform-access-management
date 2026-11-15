package cv.igrp.platform.access_management.permissions.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.permissions.application.commands.UpdatePermissionNameCommand;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionNameUpdateDTO;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionSummaryDTO;
import cv.igrp.platform.access_management.permissions.application.queries.ListPermissionsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * v3-04 (#12): the permissions App Center's settings list, by display name,
 * and the edit of a permission's name and description by its immutable key.
 * Keys {@code access-management-api.permissions.list} and
 * {@code access-management-api.permissions.update}.
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Permissions", description = "Permission display names")
public class PermissionController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public PermissionController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @GetMapping("permissions")
    @Operation(summary = "List permissions by display name",
            description = "The live keyed permissions, optionally of one application or one service, sorted by "
                    + "display name. A permission whose code gave no name shows its key.",
            responses = @ApiResponse(responseCode = "200", description = "The permissions",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = PermissionSummaryDTO.class)))))
    public ResponseEntity<List<PermissionSummaryDTO>> list(
            @RequestParam(value = "applicationCode", required = false) String applicationCode,
            @RequestParam(value = "service", required = false) String service) {
        return queryBus.handle(new ListPermissionsQuery(applicationCode, service));
    }

    @PutMapping("permissions/{key}")
    @Operation(summary = "Edit a permission's display name and description",
            description = "By the immutable key. Audited. A later registration of the service never overwrites "
                    + "the edit. 404 for an unknown key, 400 for a name in the reserved igrp. namespace, 409 for "
                    + "a name another permission has.",
            responses = @ApiResponse(responseCode = "200", description = "The permission, edited",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermissionSummaryDTO.class))))
    public ResponseEntity<PermissionSummaryDTO> update(@PathVariable("key") String key,
                                                       @Valid @RequestBody PermissionNameUpdateDTO update) {
        return commandBus.send(new UpdatePermissionNameCommand(key, update));
    }
}
