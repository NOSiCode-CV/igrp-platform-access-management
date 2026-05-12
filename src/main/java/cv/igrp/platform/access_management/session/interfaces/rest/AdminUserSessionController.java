package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.auth.generated.PermissionsRegistry;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.commands.KillAllUserSessionsCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionCommand;
import cv.igrp.platform.access_management.session.application.dto.SessionKillRequestDTO;
import cv.igrp.platform.access_management.session.domain.service.ForcedReAuthService;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Phase E4 / E5 — admin endpoints scoped under {@code /api/admin/users/{userExternalId}}
 * for session lifecycle operations targeted at a specific user.
 *
 * <p>Kept separate from {@link AdminSessionController} because the URL tree
 * (and therefore the spec's IAM-aligned shape) lives outside that controller's
 * {@code /api/admin/sessions} root.
 */
@Slf4j
@IgrpController
@RestController
@RequestMapping("/api/admin/users")
@Tag(
    name = "Admin User Session Management",
    description = "Per-user administrative session operations (Phase E4/E5)"
)
// Phase G1 / FR-13 — Layer 3 belt-and-suspenders: reject any principal without a
// sid claim (i.e. M2M client_credentials tokens) before method execution. The
// preceding M2MTokenRejectionFilter on the OAuth2 chain is the primary defense;
// this @PreAuthorize is the static-analysis-visible guarantee for this admin
// surface.
@PreAuthorize("@subjectGuard.requiresUser(authentication)")
public class AdminUserSessionController {

    private final CommandBus commandBus;
    private final SessionRepository sessionRepository;
    private final IGRPUserEntityRepository userRepository;
    private final ForcedReAuthService forcedReAuthService;
    private final SessionAuditLogger sessionAuditLogger;

    public AdminUserSessionController(CommandBus commandBus,
                                      SessionRepository sessionRepository,
                                      IGRPUserEntityRepository userRepository,
                                      ForcedReAuthService forcedReAuthService,
                                      SessionAuditLogger sessionAuditLogger) {
        this.commandBus = commandBus;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.forcedReAuthService = forcedReAuthService;
        this.sessionAuditLogger = sessionAuditLogger;
    }

    @PostMapping("/{userExternalId}/sessions/{sessionId}/kill")
    @Operation(
        summary = "Kill a specific session belonging to a user",
        description = "Terminates the given session id only when it belongs to the user identified by "
                + "externalId. Returns 404 if the user does not exist, the session does not exist, or "
                + "the session belongs to a different user."
    )
    @ApiResponse(responseCode = "204", description = "Session killed")
    @ApiResponse(responseCode = "404", description = "Session not found or does not belong to user")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(PermissionsRegistry.Permission).IGRP_SESSION_ADMIN)")
    public ResponseEntity<Void> killUserSession(
            @Parameter(description = "User external id") @PathVariable String userExternalId,
            @Parameter(description = "Session id (UUID)") @PathVariable UUID sessionId,
            @Valid @RequestBody SessionKillRequestDTO request) {

        Optional<IGRPUserEntity> user = userRepository.findByExternalId(userExternalId);
        if (user.isEmpty()) {
            log.warn("Admin kill-session refused: user externalId={} not found", userExternalId);
            return ResponseEntity.notFound().build();
        }
        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty() || !user.get().getInternalId().equals(sessionOpt.get().getUserId())) {
            log.warn("Admin kill-session refused: session {} does not belong to user externalId={}",
                    sessionId, userExternalId);
            return ResponseEntity.notFound().build();
        }
        log.info("Admin killing session {} for user externalId={} reason={} by={}",
                sessionId, userExternalId, request.getReason(), request.getKilledBy());
        String reason = request.getReason() != null ? request.getReason() : "ADMIN_KILL";
        var command = new KillSessionCommand(sessionId, reason, request.getKilledBy());
        boolean killed = commandBus.send(command);
        if (killed) {
            sessionAuditLogger.recordRevoked(sessionId, user.get().getInternalId(),
                    reason, SessionAuditLogger.adminActor(userExternalId));
        }
        return killed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{userExternalId}/logout-all")
    @Operation(
        summary = "Kill every active session for a user",
        description = "Bulk-revokes every ACTIVE session bound to the given user external id."
    )
    @ApiResponse(responseCode = "204", description = "Sessions revoked")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(PermissionsRegistry.Permission).IGRP_SESSION_ADMIN)")
    public ResponseEntity<Void> logoutAll(
            @Parameter(description = "User external id") @PathVariable String userExternalId,
            @Valid @RequestBody SessionKillRequestDTO request) {

        log.info("Admin logout-all for user externalId={} reason={} by={}",
                userExternalId, request.getReason(), request.getKilledBy());
        String reason = request.getReason() != null ? request.getReason() : "ADMIN_LOGOUT_ALL";
        var command = new KillAllUserSessionsCommand(
                userExternalId,
                reason,
                request.getKilledBy() != null ? request.getKilledBy() : "ADMIN");
        Boolean ok = commandBus.send(command);
        if (Boolean.TRUE.equals(ok)) {
            Integer internalId = userRepository.findByExternalId(userExternalId)
                    .map(u -> u.getInternalId()).orElse(null);
            sessionAuditLogger.recordRevoked(null, internalId,
                    reason, SessionAuditLogger.adminActor(userExternalId));
        }
        return Boolean.TRUE.equals(ok)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{userExternalId}/force-reauth")
    @Operation(
        summary = "Force re-authentication for a user",
        description = "Phase F1 — bumps the user-wide token validity floor (tokens_not_valid_before) "
                + "and revokes every active session. Every JWT issued before this instant — including "
                + "tokens already in flight — is rejected by the SessionEnforcementFilter. Use after a "
                + "password reset or any administrative \"log this user out everywhere\" action."
    )
    @ApiResponse(responseCode = "204", description = "Re-auth enforced")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(PermissionsRegistry.Permission).IGRP_SESSION_ADMIN)")
    public ResponseEntity<Void> forceReauth(
            @Parameter(description = "User external id") @PathVariable String userExternalId,
            @Valid @RequestBody SessionKillRequestDTO request) {

        Optional<IGRPUserEntity> user = userRepository.findByExternalId(userExternalId);
        if (user.isEmpty()) {
            log.warn("Admin force-reauth refused: user externalId={} not found", userExternalId);
            return ResponseEntity.notFound().build();
        }
        String reason = request.getReason() != null ? request.getReason() : "ADMIN_FORCE_REAUTH";
        log.info("Admin force-reauth for user externalId={} reason={} by={}",
                userExternalId, reason, request.getKilledBy());
        forcedReAuthService.forceReAuthentication(user.get().getInternalId(), reason,
                SessionAuditLogger.adminActor(userExternalId));
        return ResponseEntity.noContent().build();
    }
}
