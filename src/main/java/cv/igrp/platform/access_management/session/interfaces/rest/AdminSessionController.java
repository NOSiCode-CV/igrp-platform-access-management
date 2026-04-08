package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.dto.SessionKillRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.session.application.queries.ListSessionsQuery;
import cv.igrp.platform.access_management.session.application.queries.GetUserSessionQuery;
import cv.igrp.platform.access_management.session.application.queries.GetSessionsByRoleQuery;
import cv.igrp.platform.access_management.session.application.queries.GetSessionsByDepartmentQuery;
import cv.igrp.platform.access_management.session.application.commands.KillSessionCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionsByRoleCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionsByDepartmentCommand;
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


    private final QueryBus queryBus;
    private final CommandBus commandBus;
    private final SessionInvalidationService sessionInvalidationService;

    public AdminSessionController(
            QueryBus queryBus,
            CommandBus commandBus,
            SessionInvalidationService sessionInvalidationService) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
        this.sessionInvalidationService = sessionInvalidationService;
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
    
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<Page<SessionResponseDTO>> listSessions(
            @Parameter(description = "Session status filter") 
            @RequestParam(required = false) SessionStatus status,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {
        
        
        Pageable pageable = PageRequest.of(page, size);
        var query = new ListSessionsQuery(status, pageable);
        Page<SessionResponseDTO> sessions = queryBus.handle(query);
        
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
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<SessionResponseDTO> getUserSession(
            @Parameter(description = "User external ID") 
            @PathVariable String userExternalId) {
        
        log.debug("Admin getting session for user: {}", userExternalId);
        
        var query = new GetUserSessionQuery(userExternalId);
        Optional<SessionResponseDTO> session = queryBus.handle(query);
        
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
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<Void> killSession(
            @Parameter(description = "Session ID") 
            @PathVariable UUID sessionId,
            @Valid @RequestBody SessionKillRequestDTO request) {
        
        log.info("Admin killing session: {} with reason: {}", sessionId, request.getReason());
        
        var command = new KillSessionCommand(sessionId, request.getReason(), request.getKilledBy());
        boolean killed = commandBus.send(command);
        
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
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<Page<SessionResponseDTO>> listSessionsByRole(
            @Parameter(description = "Role code") 
            @PathVariable String roleCode,
            @Parameter(description = "Department code (optional)") 
            @RequestParam(required = false) String departmentCode,
            @Parameter(description = "Session status filter") 
            @RequestParam(required = false) SessionStatus status,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Admin listing sessions for role: {} in department: {} with status: {}", 
                roleCode, departmentCode, status);
        
        Pageable pageable = PageRequest.of(page, size);
        var query = new GetSessionsByRoleQuery(roleCode, departmentCode, status, pageable);
        Page<SessionResponseDTO> sessions = queryBus.handle(query);
        
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
    @ApiResponse(
        responseCode = "404",
        description = "No sessions found for role"
    )
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<Void> killSessionsByRole(
            @Parameter(description = "Role code") 
            @PathVariable String roleCode,
            @Parameter(description = "Department code (role codes are department-scoped)") 
            @RequestParam(required = false) String departmentCode,
            @Valid @RequestBody SessionKillRequestDTO request) {
        
        log.info("Admin killing sessions for role: {} in department: {} with reason: {}", 
                roleCode, departmentCode, request.getReason());
        
        var command = new KillSessionsByRoleCommand(roleCode, departmentCode, request.getReason(), request.getKilledBy());
        int killedCount = commandBus.send(command);
        
        if (killedCount == 0) {
            return ResponseEntity.notFound().build();
        }
        
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
    @PreAuthorize("hasAuthority('igrp.session.admin')")
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
    @PreAuthorize("hasAuthority('igrp.session.admin')")
    public ResponseEntity<Object> getSessionStatistics() {
        log.debug("Admin getting session statistics");
        
        // This could be expanded to provide more detailed statistics
        var activeQuery = new ListSessionsQuery(SessionStatus.ACTIVE, PageRequest.of(0, 1));
        var expiredQuery = new ListSessionsQuery(SessionStatus.EXPIRED, PageRequest.of(0, 1));
        var closedQuery = new ListSessionsQuery(SessionStatus.CLOSED, PageRequest.of(0, 1));
        var revokedQuery = new ListSessionsQuery(SessionStatus.REVOKED, PageRequest.of(0, 1));
        
        var stats = new Object() {
            public final long activeSessions = ((org.springframework.data.domain.Page<SessionResponseDTO>) queryBus.handle(activeQuery)).getTotalElements();
            public final long expiredSessions = ((org.springframework.data.domain.Page<SessionResponseDTO>) queryBus.handle(expiredQuery)).getTotalElements();
            public final long closedSessions = ((org.springframework.data.domain.Page<SessionResponseDTO>) queryBus.handle(closedQuery)).getTotalElements();
            public final long revokedSessions = ((org.springframework.data.domain.Page<SessionResponseDTO>) queryBus.handle(revokedQuery)).getTotalElements();
        };
        
        return ResponseEntity.ok(stats);
    }
}
