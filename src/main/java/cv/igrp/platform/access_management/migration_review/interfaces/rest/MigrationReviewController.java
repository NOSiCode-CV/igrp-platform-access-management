package cv.igrp.platform.access_management.migration_review.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.migration_review.application.commands.DecideMigrationSuggestionCommand;
import cv.igrp.platform.access_management.migration_review.application.dto.MigrationSuggestionDTO;
import cv.igrp.platform.access_management.migration_review.application.queries.ListMigrationSuggestionsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * v3-29 (#37): App Center's migration review. Keys
 * {@code access-management-api.migration.suggestions.list},
 * {@code ...migration.suggestions.confirm.create} and
 * {@code ...migration.suggestions.dismiss.create}.
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Migration review", description = "Suggestions to copy legacy permission assignments to route keys")
public class MigrationReviewController {

    private static final String DECIDED = "The suggestion, decided";

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public MigrationReviewController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @GetMapping("migration/suggestions")
    @Operation(summary = "List migration review suggestions",
            description = "Each route key whose registration named a legacy permission someone holds, with that "
                    + "permission's roles (and scopes) and departments. PENDING by default.",
            responses = @ApiResponse(responseCode = "200", description = "The suggestions",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = MigrationSuggestionDTO.class)))))
    public ResponseEntity<List<MigrationSuggestionDTO>> list(
            @RequestParam(value = "applicationCode", required = false) String applicationCode,
            @RequestParam(value = "status", required = false) String status) {
        return queryBus.handle(new ListMigrationSuggestionsQuery(applicationCode, status));
    }

    @PostMapping("migration/suggestions/{id}/confirm")
    @Operation(summary = "Confirm a suggestion",
            description = "Copies the legacy permission's roles (with their scopes) and departments to the route "
                    + "key. Audited. 404 unknown, 409 already decided.",
            responses = @ApiResponse(responseCode = "200", description = DECIDED,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MigrationSuggestionDTO.class))))
    public ResponseEntity<MigrationSuggestionDTO> confirm(@PathVariable("id") int id) {
        return commandBus.send(new DecideMigrationSuggestionCommand(id, true));
    }

    @PostMapping("migration/suggestions/{id}/dismiss")
    @Operation(summary = "Dismiss a suggestion",
            description = "Copies nothing. 404 unknown, 409 already decided.",
            responses = @ApiResponse(responseCode = "200", description = DECIDED,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MigrationSuggestionDTO.class))))
    public ResponseEntity<MigrationSuggestionDTO> dismiss(@PathVariable("id") int id) {
        return commandBus.send(new DecideMigrationSuggestionCommand(id, false));
    }
}
