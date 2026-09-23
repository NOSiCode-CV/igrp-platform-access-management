package cv.igrp.platform.access_management.oauth_server.interfaces.rest;

import cv.igrp.platform.access_management.oauth_server.application.ServiceAccountService;
import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountRequestDTO;
import cv.igrp.framework.auth.generated.PermissionsRegistry.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administrative API for service accounts.
 *
 * <p><b>Authorization inherits the OAuth-client permissions</b>
 * ({@code igrp.client.*}) rather than using a dedicated
 * {@code igrp.service_account.*} set. A service account is an identity bound to
 * an OAuth client, so whoever can create, update or delete the client can
 * already obtain the same capability through that route — a separate permission
 * set would not restrict anything, it would only create pairs of endpoints
 * where one is guarded and its equivalent is not, and two catalogs for
 * administrators to keep in step.
 */
@RestController
@RequestMapping(path = "api/service-accounts")
@Tag(name = "Service Accounts", description = "Manage OAuth client service accounts")
public class ServiceAccountController {

    private final ServiceAccountService service;

    public ServiceAccountController(ServiceAccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List service accounts",
            description = "This Permission is required: igrp.client.list")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_LIST)")
    public ResponseEntity<List<ServiceAccountDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service account by id",
            description = "This Permission is required: igrp.client.view")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_VIEW)")
    public ResponseEntity<ServiceAccountDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create service account",
            description = "This Permission is required: igrp.client.create")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_CREATE)")
    public ResponseEntity<ServiceAccountDTO> create(@Valid @RequestBody ServiceAccountRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update service account",
            description = "This Permission is required: igrp.client.update")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_UPDATE)")
    public ResponseEntity<ServiceAccountDTO> update(@PathVariable UUID id,
                                                    @Valid @RequestBody ServiceAccountRequestDTO request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete service account",
            description = "This Permission is required: igrp.client.delete")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_CLIENT_DELETE)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
