package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Closes the caller's own session, scoped by the {@code sid} claim.
 *
 * @see CloseSessionCommand
 * @see SessionManagementService#closeCallerSession(String, java.util.UUID, String)
 */
@Slf4j
@Component
public class CloseSessionCommandHandler implements CommandHandler<CloseSessionCommand, Boolean> {

    private final SessionManagementService sessionManagementService;

    public CloseSessionCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    @IgrpCommandHandler
    public Boolean handle(CloseSessionCommand command) {
        log.debug("Handling CloseSessionCommand for user: {} sid: {}",
                command.getUserId(), command.getSid());

        boolean closed = sessionManagementService.closeCallerSession(
                command.getUserId(),
                command.getSid(),
                command.getReason());

        if (!closed) {
            log.debug("No active session to close for user: {}", command.getUserId());
        }

        return closed;
    }
}
