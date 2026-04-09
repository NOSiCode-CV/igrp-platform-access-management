package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.dto.SessionInitRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionRefreshRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.session.application.queries.GetCurrentSessionQuery;
import cv.igrp.platform.access_management.session.application.commands.InitializeSessionCommand;
import cv.igrp.platform.access_management.session.application.commands.RefreshSessionCommand;
import cv.igrp.platform.access_management.session.application.commands.CloseSessionCommand;
import cv.igrp.platform.access_management.session.application.commands.RotateSessionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@IgrpController
@RestController
@RequestMapping("/api/session")
@Tag(
    name = "Session Management",
    description = "User session management endpoints"
)
public class SessionController {

    private final QueryBus queryBus;
    private final CommandBus commandBus;
    private final AuthenticationHelper authenticationHelper;

    public SessionController(
            QueryBus queryBus,
            CommandBus commandBus,
            AuthenticationHelper authenticationHelper) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
        this.authenticationHelper = authenticationHelper;
    }

    @GetMapping
    @Operation(
        summary = "Get current session",
        description = "Retrieves the current active session for the authenticated user"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session found",
        content = @Content(schema = @Schema(implementation = SessionResponseDTO.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No active session found"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponseDTO> getCurrentSession() {
        String userExternalId = authenticationHelper.getSub();
        log.debug("Getting current session for user: {}", userExternalId);

        var query = new GetCurrentSessionQuery(userExternalId);
        Optional<SessionResponseDTO> session = queryBus.handle(query);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/init")
    @Operation(
        summary = "Initialize session",
        description = "Creates a new session for the authenticated user, closing any existing active session"
    )
    @ApiResponse(
        responseCode = "201",
        description = "Session created successfully",
        content = @Content(schema = @Schema(implementation = SessionResponseDTO.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "User account is not active"
    )
    @ApiResponse(
        responseCode = "404",
        description = "User not found"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponseDTO> initializeSession(
            @Valid @RequestBody SessionInitRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceId = request.getDeviceId();
        String userExternalId = authenticationHelper.getSub();
        
        log.info("Initializing session for user: {}", userExternalId);
        
        var command = new InitializeSessionCommand(userExternalId, clientIp, userAgent, deviceId);
        SessionResponseDTO session = commandBus.send(command);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh session",
        description = "Extends the expiration time of the current active session"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session refreshed successfully",
        content = @Content(schema = @Schema(implementation = SessionResponseDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No active session to refresh"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponseDTO> refreshSession(
            @Valid @RequestBody(required = false) SessionRefreshRequestDTO request) {
        
        String userExternalId = authenticationHelper.getSub();
        log.debug("Refreshing session for user: {}", userExternalId);

        Integer extensionSeconds = request != null ? request.getExtensionSeconds() : null;
        
        var command = new RefreshSessionCommand(userExternalId, extensionSeconds);
        Optional<SessionResponseDTO> session = commandBus.send(command);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/close")
    @Operation(
        summary = "Close session",
        description = "Closes the current active session for the authenticated user"
    )
    @ApiResponse(
        responseCode = "204",
        description = "Session closed successfully"
    )
    @ApiResponse(
        responseCode = "404",
        description = "No active session to close"
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> closeSession() {
        String userExternalId = authenticationHelper.getSub();
        log.info("Closing session for user: {}", userExternalId);

        var command = new CloseSessionCommand(userExternalId, "USER_CLOSED");
        boolean closed = commandBus.send(command);

        return closed ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/rotate")
    @Operation(
        summary = "Rotate session",
        description = "Closes the current session and creates a new one (session fixation protection)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session rotated successfully",
        content = @Content(schema = @Schema(implementation = SessionResponseDTO.class))
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponseDTO> rotateSession(
            @Valid @RequestBody SessionInitRequestDTO request,
            HttpServletRequest httpRequest) {
        
        String userExternalId = authenticationHelper.getSub();
        log.info("Rotating session for user: {}", userExternalId);

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceId = request.getDeviceId();

        var command = new RotateSessionCommand(userExternalId, clientIp, userAgent, deviceId);
        Optional<SessionResponseDTO> session = commandBus.send(command);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Extract client IP from HTTP request
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
