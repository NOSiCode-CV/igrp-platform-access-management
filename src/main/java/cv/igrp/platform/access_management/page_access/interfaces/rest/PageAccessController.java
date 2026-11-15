package cv.igrp.platform.access_management.page_access.interfaces.rest;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.page_access.application.dto.PageAccessDecisionDTO;
import cv.igrp.platform.access_management.page_access.application.dto.PageAccessRequestDTO;
import cv.igrp.platform.access_management.page_access.application.queries.CheckPageAccessQuery;
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
 * v3-26 (#34): the page check every frontend's middleware calls before it
 * renders a page, with the user's own token. Self-service: any signed-in user
 * may ask about themselves ({@code SelfServicePaths}).
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Page access", description = "Whether the caller may open a frontend page")
public class PageAccessController {

    private final QueryBus queryBus;

    public PageAccessController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @PostMapping("authorize/page")
    @Operation(summary = "May the caller open this page?",
            description = "Default deny. Root is allowed; otherwise the caller needs a role valid for the "
                    + "application, a registered page that serves the path, an active role, and every required "
                    + "key of the page. 200 with allowed false and the reason when denied.",
            responses = @ApiResponse(responseCode = "200", description = "The decision and its reason",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageAccessDecisionDTO.class))))
    public ResponseEntity<PageAccessDecisionDTO> check(@Valid @RequestBody PageAccessRequestDTO request) {
        return queryBus.handle(new CheckPageAccessQuery(request.applicationCode(), request.path()));
    }
}
