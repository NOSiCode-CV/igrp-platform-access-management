package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Command handler responsible for terminating all sessions for users belonging to a specific role/department.
 *
 * <p>
 * This handler receives a {@link KillSessionsByRoleCommand} containing role code, department code, reason, and killer details,
 * delegates to the {@link SessionManagementService} to terminate the sessions,
 * and returns the number of sessions that were successfully killed.
 * </p>
 *
 * @see KillSessionsByRoleCommand
 * @see SessionManagementService
 */
@Slf4j
@Component
public class KillSessionsByRoleCommandHandler implements CommandHandler<KillSessionsByRoleCommand, Integer> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to terminate and manage sessions
     */
    public KillSessionsByRoleCommandHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the command to terminate all sessions for users belonging to a specific role/department.
     *
     * @param command the {@link KillSessionsByRoleCommand} containing role, department, and termination details
     * @return the number of sessions that were successfully terminated
     */
    @IgrpCommandHandler
    public Integer handle(KillSessionsByRoleCommand command) {
        log.info("Handling KillSessionsByRoleCommand for role: {} in department: {} with reason: {} by {}", 
                command.getRoleCode(), command.getDepartmentCode(), command.getReason(), command.getKilledBy());
        
        int killedCount = sessionManagementService.killSessionsByRole(
                command.getRoleCode(),
                command.getDepartmentCode(),
                command.getReason(),
                command.getKilledBy()
        );
        
        log.info("Successfully terminated {} sessions for role: {} in department: {}", 
                killedCount, command.getRoleCode(), command.getDepartmentCode());
        
        return killedCount;
    }
}
