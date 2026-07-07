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

@RestController
@RequestMapping(path = "api/service-accounts")
@Tag(name = "Service Accounts", description = "Manage OAuth client service accounts")
public class ServiceAccountController {

    private final ServiceAccountService service;

    public ServiceAccountController(ServiceAccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List service accounts")
    @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_SERVICE_ACCOUNT_LIST, T(Permission).IGRP_SERVICE_ACCOUNT_MANAGE)")
    public ResponseEntity<List<ServiceAccountDTO>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service account by id")
    @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_SERVICE_ACCOUNT_VIEW, T(Permission).IGRP_SERVICE_ACCOUNT_MANAGE)")
    public ResponseEntity<ServiceAccountDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create service account")
    @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_SERVICE_ACCOUNT_CREATE, T(Permission).IGRP_SERVICE_ACCOUNT_MANAGE)")
    public ResponseEntity<ServiceAccountDTO> create(@Valid @RequestBody ServiceAccountRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update service account")
    @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_SERVICE_ACCOUNT_UPDATE, T(Permission).IGRP_SERVICE_ACCOUNT_MANAGE)")
    public ResponseEntity<ServiceAccountDTO> update(@PathVariable UUID id,
                                                    @Valid @RequestBody ServiceAccountRequestDTO request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete service account")
    @PreAuthorize("@igrpAuthorization.checkAnyPermission(T(Permission).IGRP_SERVICE_ACCOUNT_DELETE, T(Permission).IGRP_SERVICE_ACCOUNT_MANAGE)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
