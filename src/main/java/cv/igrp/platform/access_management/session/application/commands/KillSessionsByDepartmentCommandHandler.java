package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command handler responsible for terminating all sessions for users belonging to a specific department.
 *
 * <p>
 * This handler receives a {@link KillSessionsByDepartmentCommand} containing department code, reason, and killer details,
 * delegates to the {@link SessionManagementService} to terminate the sessions,
 * and returns the number of sessions that were successfully killed.
 * </p>
 *
 * @see KillSessionsByDepartmentCommand
 * @see SessionManagementService
 */
@Slf4j
@Component
public class KillSessionsByDepartmentCommandHandler implements CommandHandler<KillSessionsByDepartmentCommand, Integer> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to terminate and manage sessions
     */
    public KillSessionsByDepartmentCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to terminate all sessions for users belonging to a specific department.
     * Since killSessionsByDepartment is not available in the service, this implementation
     * uses a workaround by finding all active sessions in the department and killing them individually.
     *
     * @param command the {@link KillSessionsByDepartmentCommand} containing department and termination details
     * @return the number of sessions that were successfully terminated
     */
    @IgrpCommandHandler
    public Integer handle(KillSessionsByDepartmentCommand command) {
        log.info("Handling KillSessionsByDepartmentCommand for department: {} with reason: {} by {}", 
                command.getDepartmentCode(), command.getReason(), command.getKilledBy());
        
        // Get all active sessions for the department
        var activeSessions = sessionManagementService.listSessionsByDepartment(
                command.getDepartmentCode(),
                cv.igrp.platform.access_management.session.domain.constants.SessionStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 1000) // Large page size to get all sessions
        );
        
        int killedCount = 0;
        for (var session : activeSessions.getContent()) {
            boolean killed = sessionManagementService.killSession(
                    session.getSessionId(),
                    command.getReason(),
                    command.getKilledBy()
            );
            if (killed) {
                killedCount++;
            }
        }
        
        log.info("Successfully terminated {} sessions for department: {}", 
                killedCount, command.getDepartmentCode());
        
        return killedCount;
    }
}
