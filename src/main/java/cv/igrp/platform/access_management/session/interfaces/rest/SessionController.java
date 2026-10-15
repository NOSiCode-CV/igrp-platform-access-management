package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.dto.SessionCheckResponseDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionInitRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionRefreshRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.access_management.session.application.queries.GetCurrentSessionQuery;
import cv.igrp.platform.access_management.session.application.queries.GetSessionCheckQuery;
import cv.igrp.platform.access_management.session.application.commands.CloseSessionCommand;
import cv.igrp.platform.access_management.session.application.commands.RefreshSessionCommand;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

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
        String userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        UUID sid = extractSid();
        log.debug("Getting current session for user: {} sid: {}", userId, sid);

        var query = new GetCurrentSessionQuery(userId, sid);
        Optional<SessionResponseDTO> session = queryBus.handle(query);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Best-effort read of the JWT's {@code sid} claim — populated by
     * {@code SessionIssuanceService} at token issuance and used to
     * disambiguate WHICH of the caller's active sessions to operate on
     * (a user can hold up to {@code IGRP_SESSION_MAX_PER_USER} sessions
     * concurrently). Returns null if the claim is missing or malformed;
     * downstream services fall back to "the user's most recently seen
     * active session" in that case.
     */
    private UUID extractSid() {
        try {
            Jwt jwt = authenticationHelper.getJwtToken();
            String sid = jwt.getClaimAsString("sid");
            return (sid != null && !sid.isBlank()) ? UUID.fromString(sid) : null;
        } catch (RuntimeException e) {
            // No JWT (M2M path) or non-UUID sid value — legacy path.
            return null;
        }
    }

    @GetMapping("/check")
    @Operation(
        summary = "Check session validity",
        description = "Returns the combined JWT + server-side session state for the authenticated caller. "
                + "This endpoint is exempt from the SessionEnforcementFilter so a revoked-session JWT can "
                + "still receive valid=false with a reason instead of an opaque 401."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Session check result",
        content = @Content(schema = @Schema(implementation = SessionCheckResponseDTO.class))
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionCheckResponseDTO> checkSession() {
        Jwt jwt = authenticationHelper.getJwtToken();
        String sub = jwt.getClaimAsString("sub");
        String sidClaim = jwt.getClaimAsString("sid");

        UUID sid = null;
        if (sidClaim != null && !sidClaim.isBlank()) {
            try {
                sid = UUID.fromString(sidClaim);
            } catch (IllegalArgumentException ignored) {
                // sid malformed → handler returns valid=false with MISSING_SID/SESSION_NOT_FOUND
            }
        }

        var query = new GetSessionCheckQuery(sid, sub);
        SessionCheckResponseDTO response = queryBus.handle(query);
        return ResponseEntity.ok(response);
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

        String userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        log.debug("Refreshing session for user: {}", userId);

        Integer extensionSeconds = request != null ? request.getExtensionSeconds() : null;

        // sid scopes the operation to THIS caller's session. Several apps in one
        // browser each hold their own; without it the service falls back to the
        // user's most recently seen session, which is often a sibling's.
        var command = new RefreshSessionCommand(userId, extractSid(), extensionSeconds);
        Optional<SessionResponseDTO> session = commandBus.send(command);

        return session.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rotate")
    @Operation(
        summary = "Rotate session (not implemented)",
        description = "Always returns 501.\n\n"
                + "Rotation means replacing the session identifier, and the new sid is only useful once a "
                + "token carries it — which only the token endpoint can do. A REST implementation can close "
                + "the old row and open a new one, but nothing hands the caller the new sid, so its access "
                + "token still presents the old one and every subsequent call fails with session_revoked. "
                + "In other words, calling this would sign the caller out and leave an orphan row behind, "
                + "counting against the per-user session cap.\n\n"
                + "Use the refresh grant at /oauth2/token: it rotates the session and returns tokens "
                + "carrying the new sid."
    )
    @ApiResponse(
        responseCode = "501",
        description = "Rotation is not available over REST — use the refresh grant",
        content = @Content
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionResponseDTO> rotateSession(
            @Valid @RequestBody SessionInitRequestDTO request,
            HttpServletRequest httpRequest) {

        String userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        log.warn("Rejected /api/session/rotate for user {}: rotation cannot be performed over REST", userId);

        throw IgrpResponseStatusException.of(HttpStatus.NOT_IMPLEMENTED,
                "Session rotation is not available over REST. Rotating a session means issuing a new sid, "
                        + "and only the token endpoint can put that sid into a token. Closing the old row "
                        + "here would simply sign the calling application out, because its access token "
                        + "still carries the old sid. Obtain fresh tokens through the refresh grant at "
                        + "/oauth2/token instead; that rotates the session properly and hands you the new "
                        + "sid in the response.");
    }

    @PostMapping("/close")
    @Operation(
        summary = "Close the current session",
        description = "Closes the caller's own session server-side, identified by the sid claim of the "
                + "access token. The next request carrying that sid is rejected.\n\n"
                + "This is NOT a logout. It does not clear browser cookies, does not end the SSO session "
                + "on this server, does not revoke the caller's tokens, and does not cascade to the "
                + "upstream identity provider — so a subsequent /oauth2/authorize may well sign the user "
                + "straight back in without a prompt. To actually sign a user out, use the OIDC "
                + "end_session endpoint (/connect/logout) with an id_token_hint.\n\n"
                + "Use this when an application wants to drop its own server-side session while leaving "
                + "the browser's login alone — sibling applications on the same browser are unaffected."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session closed", content = @Content),
        @ApiResponse(responseCode = "404", description = "The caller has no active session", content = @Content)
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> closeSession() {
        String userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        log.info("Closing session for user: {}", userId);

        var command = new CloseSessionCommand(userId, extractSid(), "USER_CLOSED");
        Boolean closed = commandBus.send(command);

        return Boolean.TRUE.equals(closed)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
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
