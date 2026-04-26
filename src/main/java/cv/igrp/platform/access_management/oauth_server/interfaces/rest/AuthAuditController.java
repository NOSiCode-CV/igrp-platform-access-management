package cv.igrp.platform.access_management.oauth_server.interfaces.rest;

import cv.igrp.platform.access_management.oauth_server.application.dto.AuthAuditDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.security.AuthAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to OAuth authorization server audit events.
 */
@RestController("oauthServerAuthAuditController")
@RequestMapping(path = "api/audit")
@Tag(name = "OAuth Audit", description = "OAuth2 authorization server audit log")
public class AuthAuditController {

    private final AuthAuditService auditService;

    public AuthAuditController(AuthAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List all auth audit logs", description = "Returns all recorded authentication events, newest first.")
    public ResponseEntity<List<AuthAuditDTO>> list() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }

    @GetMapping("/user/{username}")
    @Operation(summary = "List auth audit logs by username")
    public ResponseEntity<List<AuthAuditDTO>> byUser(@PathVariable String username) {
        return ResponseEntity.ok(auditService.getLogsByUser(username));
    }
}
