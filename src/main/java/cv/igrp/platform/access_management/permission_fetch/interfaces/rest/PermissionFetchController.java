package cv.igrp.platform.access_management.permission_fetch.interfaces.rest;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.application.queries.FetchPermissionsQuery;
import cv.igrp.platform.access_management.permission_fetch.config.PermissionSetProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v3-06 (#14): {@code GET /api/users/me/permissions?applicationCode=}, called
 * by the 0.3 starter on a cache miss with the caller's own token.
 *
 * <p>Mapped only when {@code applicationCode} is present: the legacy
 * {@code GET /api/users/me/permissions[?roleCode=]} (a permission list, used by
 * the 0.2 SDKs) keeps answering without it until #38.
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@EnableConfigurationProperties(PermissionSetProperties.class)
@Tag(name = "Permission fetch", description = "The caller's permission set for one application")
public class PermissionFetchController {

    private final QueryBus queryBus;

    public PermissionFetchController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping(value = "users/me/permissions", params = "applicationCode")
    @Operation(summary = "Fetch my permission set for an application",
            description = "Keys of the caller's active role in the application (a service account: its roles and "
                    + "direct grants), with their scopes. Also writes them to the shared Redis. 401 without an "
                    + "active authorization.",
            responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = PermissionSetDTO.class))))
    public ResponseEntity<PermissionSetDTO> fetch(@RequestParam("applicationCode") String applicationCode) {
        return queryBus.handle(new FetchPermissionsQuery(applicationCode));
    }
}
