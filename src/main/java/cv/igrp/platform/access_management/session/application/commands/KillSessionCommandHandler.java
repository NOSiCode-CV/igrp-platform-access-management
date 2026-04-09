package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command handler responsible for terminating a specific session by ID.
 *
 * <p>
 * This handler receives a {@link KillSessionCommand} containing session ID, reason, and killer details,
 * delegates to the {@link SessionManagementService} to terminate the session,
 * and returns a boolean indicating whether the session was successfully killed.
 * </p>
 *
 * @see KillSessionCommand
 * @see SessionManagementService
 */
@Slf4j
@Component
public class KillSessionCommandHandler implements CommandHandler<KillSessionCommand, Boolean> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to terminate and manage sessions
     */
    public KillSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to terminate a specific session by ID.
     *
     * @param command the {@link KillSessionCommand} containing session termination details
     * @return {@code true} if the session was successfully terminated, {@code false} otherwise
     */
    @IgrpCommandHandler
    public Boolean handle(KillSessionCommand command) {
        log.info("Handling KillSessionCommand for session: {} with reason: {} by {}", 
                command.getSessionId(), command.getReason(), command.getKilledBy());
        
        boolean killed = sessionManagementService.killSession(
                command.getSessionId(),
                command.getReason(),
                command.getKilledBy()
        );
        
        if (killed) {
            log.info("Session {} terminated successfully", command.getSessionId());
        } else {
            log.debug("Session {} not found or already terminated", command.getSessionId());
        }
        
        return killed;
    }
}
