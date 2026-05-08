package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.auth.generated.PermissionsRegistry;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.session.application.commands.KillAllUserSessionsCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionCommand;
import cv.igrp.platform.access_management.session.application.dto.SessionKillRequestDTO;
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
public class AdminUserSessionController {

    private final CommandBus commandBus;
    private final SessionRepository sessionRepository;
    private final IGRPUserEntityRepository userRepository;

    public AdminUserSessionController(CommandBus commandBus,
                                      SessionRepository sessionRepository,
                                      IGRPUserEntityRepository userRepository) {
        this.commandBus = commandBus;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
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
        var command = new KillSessionCommand(sessionId, request.getReason(), request.getKilledBy());
        boolean killed = commandBus.send(command);
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
        var command = new KillAllUserSessionsCommand(
                userExternalId,
                request.getReason() != null ? request.getReason() : "ADMIN_LOGOUT_ALL",
                request.getKilledBy() != null ? request.getKilledBy() : "ADMIN");
        Boolean ok = commandBus.send(command);
        return Boolean.TRUE.equals(ok)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
