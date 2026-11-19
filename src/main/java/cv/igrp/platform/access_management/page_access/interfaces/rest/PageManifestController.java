package cv.igrp.platform.access_management.page_access.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.page_access.application.commands.GrantPageCommand;
import cv.igrp.platform.access_management.page_access.application.commands.RegisterManifestsCommand;
import cv.igrp.platform.access_management.page_access.application.dto.ApplicationPageDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationResultDTO;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantRequestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantResultDTO;
import cv.igrp.platform.access_management.page_access.application.queries.ListApplicationPagesQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * v3-27 (#35): frontends register their page manifests; administrators list an
 * application's pages and grant a page to a role.
 */
@IgrpController
@RestController
@RequestMapping(path = "api")
@Tag(name = "Page manifests", description = "Frontend pages, as their manifests declare them")
public class PageManifestController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public PageManifestController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping("manifests/register")
    @Operation(summary = "Register a frontend's page manifests",
            description = "Service-account tokens only; the pages belong to the client's application. Any version "
                    + "creates the pages it is missing; only the newest version seen changes a page and deletes "
                    + "the pages it no longer declares.")
    public ResponseEntity<ManifestRegistrationResultDTO> register(
            @Valid @RequestBody ManifestRegistrationRequestDTO registration) {
        return commandBus.send(new RegisterManifestsCommand(registration));
    }

    @GetMapping("applications/{code}/pages")
    @Operation(summary = "List an application's registered pages, with their required and optional keys")
    public ResponseEntity<List<ApplicationPageDTO>> pages(@PathVariable String code) {
        return queryBus.handle(new ListApplicationPagesQuery(code));
    }

    @PostMapping("departments/{departmentCode}/roles/{roleCode}/pages")
    @Operation(summary = "Grant a page to a role",
            description = "Assigns the page's required keys and the chosen optional ones, through the role's "
                    + "permission grant. All or nothing: 409 when the role cannot hold every key.")
    public ResponseEntity<PageGrantResultDTO> grant(@PathVariable String departmentCode,
                                                    @PathVariable String roleCode,
                                                    @Valid @RequestBody PageGrantRequestDTO request) {
        return commandBus.send(new GrantPageCommand(departmentCode, roleCode, request));
    }
}
