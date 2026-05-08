package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command handler responsible for closing an existing session for a user.
 *
 * <p>
 * This handler receives a {@link CloseSessionCommand} containing user details and close reason,
 * delegates to the {@link SessionManagementService} to close the session,
 * and returns a boolean indicating whether the session was successfully closed.
 * </p>
 *
 * @see CloseSessionCommand
 * @see SessionManagementService
 */
@Slf4j
@Component
public class CloseSessionCommandHandler implements CommandHandler<CloseSessionCommand, Boolean> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to close and manage sessions
     */
    public CloseSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to close an existing session.
     *
     * @param command the {@link CloseSessionCommand} containing session close details
     * @return {@code true} if the session was successfully closed, {@code false} otherwise
     */
    @IgrpCommandHandler
    public Boolean handle(CloseSessionCommand command) {
        log.info("Handling CloseSessionCommand for user: {} with reason: {}", 
                command.getUserId(), command.getReason());
        
        boolean closed = sessionManagementService.closeSession(command.getUserId(), command.getReason());
        
        if (closed) {
            log.info("Session closed successfully for user: {}", command.getUserId());
        } else {
            log.debug("No active session found to close for user: {}", command.getUserId());
        }
        
        return closed;
    }
}
