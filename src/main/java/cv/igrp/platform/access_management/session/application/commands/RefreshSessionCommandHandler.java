package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Command handler responsible for refreshing an existing session for a user.
 *
 * <p>
 * This handler receives a {@link RefreshSessionCommand} containing user details and optional extension time,
 * delegates to the {@link SessionManagementService} to extend the session,
 * and returns an {@link Optional} containing the refreshed {@link SessionResponseDTO}.
 * </p>
 *
 * @see RefreshSessionCommand
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class RefreshSessionCommandHandler implements CommandHandler<RefreshSessionCommand, Optional<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to refresh and manage sessions
     */
    public RefreshSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to refresh an existing session.
     *
     * @param command the {@link RefreshSessionCommand} containing session refresh details
     * @return an {@link Optional} containing the refreshed {@link SessionResponseDTO} if successful
     */
    @IgrpCommandHandler
    public Optional<SessionResponseDTO> handle(RefreshSessionCommand command) {
        log.debug("Handling RefreshSessionCommand for user: {} with extension: {} seconds", 
                command.getUserId(), command.getExtensionSeconds());
        
        Optional<SessionResponseDTO> session = sessionManagementService.refreshSession(
                command.getUserId(),
                command.getExtensionSeconds()
        );
        
        if (session.isPresent()) {
            log.debug("Session refreshed successfully for user: {}", command.getUserId());
        } else {
            log.debug("No active session found to refresh for user: {}", command.getUserId());
        }
        
        return session;
    }
}
