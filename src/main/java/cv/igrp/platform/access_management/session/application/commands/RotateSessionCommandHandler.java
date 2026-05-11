package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Command handler responsible for rotating a session (session fixation protection).
 *
 * <p>
 * This handler receives a {@link RotateSessionCommand} containing user session details,
 * delegates to the {@link SessionManagementService} to rotate the session,
 * and returns an {@link Optional} containing the new {@link SessionResponseDTO}.
 * </p>
 *
 * @see RotateSessionCommand
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class RotateSessionCommandHandler implements CommandHandler<RotateSessionCommand, Optional<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to rotate and manage sessions
     */
    public RotateSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to rotate a session (close current and create new one).
     *
     * @param command the {@link RotateSessionCommand} containing session rotation details
     * @return an {@link Optional} containing the new {@link SessionResponseDTO} if successful
     */
    @IgrpCommandHandler
    public Optional<SessionResponseDTO> handle(RotateSessionCommand command) {
        log.info("Handling RotateSessionCommand for user: {}", command.getUserId());
        
        Optional<SessionResponseDTO> session = sessionManagementService.rotateSession(
                command.getUserId(),
                command.getClientIp(),
                command.getUserAgent(),
                command.getDeviceId()
        );
        
        if (session.isPresent()) {
            log.info("Session rotated successfully for user: {} with new session ID: {}", 
                    command.getUserId(), session.get().getSessionId());
        } else {
            log.debug("No active session found to rotate for user: {}", command.getUserId());
        }
        
        return session;
    }
}
