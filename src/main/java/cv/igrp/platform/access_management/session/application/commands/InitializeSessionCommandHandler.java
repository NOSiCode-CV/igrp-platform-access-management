package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command handler responsible for initializing a new session for a user.
 *
 * <p>
 * This handler receives an {@link InitializeSessionCommand} containing user session details,
 * delegates to the {@link SessionManagementService} to create the session,
 * and returns the created {@link SessionResponseDTO}.
 * </p>
 *
 * @see InitializeSessionCommand
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class InitializeSessionCommandHandler implements CommandHandler<InitializeSessionCommand, SessionResponseDTO> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to create and manage sessions
     */
    public InitializeSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to initialize a new session.
     *
     * @param command the {@link InitializeSessionCommand} containing session initialization details
     * @return the created {@link SessionResponseDTO}
     */
    @IgrpCommandHandler
    public SessionResponseDTO handle(InitializeSessionCommand command) {
        log.info("Handling InitializeSessionCommand for user: {}", command.getUserId());
        
        SessionResponseDTO session = sessionManagementService.initializeSession(
                command.getUserId(),
                command.getClientIp(),
                command.getUserAgent(),
                command.getDeviceId()
        );
        
        log.info("Session initialized successfully for user: {} with session ID: {}", 
                command.getUserId(), session.getSessionId());
        
        return session;
    }
}
