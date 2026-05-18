package cv.igrp.platform.access_management.oauth_server.interfaces.rest;

import cv.igrp.platform.access_management.oauth_server.application.OAuthClientService;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administrative API for managing OAuth2 clients persisted in the platform.
 */
@RestController
@RequestMapping(path = "api/clients")
@Tag(name = "OAuth Clients", description = "Manage OAuth2 clients")
public class OAuthClientController {

    private final OAuthClientService service;

    public OAuthClientController(OAuthClientService service) {
        this.service = service;
    }

    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_LIST)")
    @GetMapping
    @Operation(summary = "List OAuth clients", description = "This Permission is required: igrp.client.list")
    public ResponseEntity<List<OAuthClientDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_VIEW)")
    @GetMapping("/{id}")
    @Operation(summary = "Get OAuth client by id", description = "This Permission is required: igrp.client.view")
    public ResponseEntity<OAuthClientDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_CREATE)")
    @PostMapping
    @Operation(summary = "Create OAuth client", description = "Returns the generated raw client secret in the response body; it will not be available again. This Permission is required: igrp.client.create")
    public ResponseEntity<OAuthClientDTO> create(@Valid @RequestBody OAuthClientRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_UPDATE)")
    @PutMapping("/{id}")
    @Operation(summary = "Update OAuth client", description = "This Permission is required: igrp.client.update")
    public ResponseEntity<OAuthClientDTO> update(@PathVariable UUID id,
                                                 @Valid @RequestBody OAuthClientRequestDTO request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_DELETE)")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete OAuth client", description = "This Permission is required: igrp.client.delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
