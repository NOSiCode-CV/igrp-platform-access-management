package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.dto.SessionKillRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@IgrpController
@RestController
@RequestMapping("/api/admin/sessions")
@Tag(
    name = "Admin Session Management",
    description = "Administrative session management endpoints"
)
public class AdminSessionController {

    private static final String SUPER_ADMIN_ROLE = "DEPT_IGRP.superadmin";

    private final SessionManagementService sessionManagementService;
    private final SessionInvalidationService sessionInvalidationService;

    public AdminSessionController(
            SessionManagementService sessionManagementService,
            SessionInvalidationService sessionInvalidationService) {
        this.sessionManagementService = sessionManagementService;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @GetMapping("/test")
    @Operation(
        summary = "Test admin access",
        description = "Simple test to verify admin controller is accessible"
    )
    @PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    public ResponseEntity<String> testAdminAccess() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        log.info(" TEST - Current user: {}", auth.getName());
        log.info(" TEST - User authorities: {}", auth.getAuthorities());
        log.info(" TEST - Looking for role: {}", "ROLE_" + SUPER_ADMIN_ROLE);
        log.info(" TEST - Has required role: {}", auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + SUPER_ADMIN_ROLE)));
        
        return ResponseEntity.ok("Admin access test - SUCCESS");
    }

    @GetMapping
    @Operation(
        summary = "List sessions",
        description = "Lists sessions with optional status filtering and pagination"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Sessions retrieved successfully",
        content = @Content(schema = @Schema(implementation = Page.class))
    )
    //@PreAuthorize("hasAuthority('SESSION_MANAGEMENT')")
    //@PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Page<SessionResponseDTO>> listSessions(
            @Parameter(description = "Session status filter") 
            @RequestParam(required = false) SessionStatus status,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {
        
        // Debug: Log current user authorities
        var auth = SecurityContextHolder.getContext().getAuthentication();
        log.info(" Current user: {}", auth.getName());
        log.info(" User authorities: {}", auth.getAuthorities());
        log.info(" Looking for role: {}", "ROLE_" + SUPER_ADMIN_ROLE);
        log.info(" Has required role: {}", auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + SUPER_ADMIN_ROLE)));
        
        log.debug("Admin listing sessions with status: {}, page: {}, size: {}", status, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<SessionResponseDTO> sessions = sessionManagementService.listSessions(status, pageable);
        
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/users/{userExternalId}")
    @Operation(
        summary = "Get user session",
        description = "Retrieves the current active session for a specific user"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session found",
        content = @Content(schema = @Schema(implementation = SessionResponseDTO.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No active session found for user"
    )
    //@PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<SessionResponseDTO> getUserSession(
            @Parameter(description = "User external ID") 
            @PathVariable String userExternalId) {
        
        log.debug("Admin getting session for user: {}", userExternalId);
        
        Optional<SessionResponseDTO> session = sessionManagementService.getCurrentSession(userExternalId);
        
        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{sessionId}/kill")
    @Operation(
        summary = "Kill session",
        description = "Terminates a specific session by ID"
    )
    @ApiResponse(
        responseCode = "204",
        description = "Session killed successfully"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Session not found"
    )
    //@PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Void> killSession(
            @Parameter(description = "Session ID") 
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionKillRequestDTO request) {
        
        log.info("Admin killing session: {} with reason: {}", sessionId, request.getReason());
        
        boolean killed = sessionManagementService.killSession(
                sessionId, request.getReason(), request.getKilledBy());
        
        return killed ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/roles/{roleCode}")
    @Operation(
        summary = "List sessions by role",
        description = "Retrieves sessions for users belonging to a specific role/department"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Sessions retrieved successfully",
        content = @Content(schema = @Schema(implementation = Page.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No sessions found for role"
    )
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Page<SessionResponseDTO>> listSessionsByRole(
            @Parameter(description = "Role code") 
            @PathVariable String roleCode,
            @Parameter(description = "Department code (optional)") 
            @RequestParam(required = false) String departmentCode,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Admin listing sessions for role: {} in department: {}", roleCode, departmentCode);
        
        // TODO: Implement role-based session filtering in SessionManagementService
        // For now, return all sessions (you'll need to add this logic)
        Pageable pageable = PageRequest.of(page, size);
        Page<SessionResponseDTO> sessions = sessionManagementService.listSessions(null, pageable);
        
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/roles/{roleCode}/kill")
    @Operation(
        summary = "Kill sessions by role",
        description = "Terminates all active sessions for users with a specific role"
    )
    @ApiResponse(
        responseCode = "204",
        description = "Sessions killed successfully"
    )
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Void> killSessionsByRole(
            @Parameter(description = "Role code") 
            @PathVariable String roleCode,
            @Parameter(description = "Department code (role codes are department-scoped)") 
            @RequestParam(required = false) String departmentCode,
            @Valid @RequestBody SessionKillRequestDTO request) {
        
        log.info("Admin killing sessions for role: {} in department: {} with reason: {}", 
                roleCode, departmentCode, request.getReason());
        
        if (departmentCode == null || departmentCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        sessionInvalidationService.invalidateSessionsByRole(
                departmentCode, roleCode, request.getReason());
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/departments/{departmentCode}/kill")
    @Operation(
        summary = "Kill sessions by department",
        description = "Terminates all active sessions for users in a specific department"
    )
    @ApiResponse(
        responseCode = "204",
        description = "Sessions killed successfully"
    )
    //@PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Void> killSessionsByDepartment(
            @Parameter(description = "Department code") 
            @PathVariable String departmentCode,
            @Valid @RequestBody SessionKillRequestDTO request) {
        
        log.info("Admin killing sessions for department: {} with reason: {}", 
                departmentCode, request.getReason());
        
        sessionInvalidationService.invalidateSessionsByDepartment(
                departmentCode, request.getReason());
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    @Operation(
        summary = "Get session statistics",
        description = "Retrieves statistics about current sessions and invalidation operations"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Statistics retrieved successfully"
    )
    //@PreAuthorize("hasRole('" + SUPER_ADMIN_ROLE + "')")
    @PreAuthorize("hasAuthority('DEPT_IGRP.superadmin')")
    public ResponseEntity<Object> getSessionStatistics() {
        log.debug("Admin getting session statistics");
        
        // This could be expanded to provide more detailed statistics
        var stats = new Object() {
            public final long activeSessions = sessionManagementService.listSessions(
                    SessionStatus.ACTIVE, PageRequest.of(0, 1)).getTotalElements();
            public final long expiredSessions = sessionManagementService.listSessions(
                    SessionStatus.EXPIRED, PageRequest.of(0, 1)).getTotalElements();
            public final long closedSessions = sessionManagementService.listSessions(
                    SessionStatus.CLOSED, PageRequest.of(0, 1)).getTotalElements();
            public final long revokedSessions = sessionManagementService.listSessions(
                    SessionStatus.REVOKED, PageRequest.of(0, 1)).getTotalElements();
        };
        
        return ResponseEntity.ok(stats);
    }
}
