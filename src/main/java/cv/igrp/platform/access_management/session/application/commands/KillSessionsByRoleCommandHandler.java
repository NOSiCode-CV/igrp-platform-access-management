package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.UserAccessKill;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Kills every user holding a role (#19), each on the whole platform.
 *
 * @see UserAccessKill
 */
@Slf4j
@Component
public class KillSessionsByRoleCommandHandler implements CommandHandler<KillSessionsByRoleCommand, Integer> {

    private final IGRPUserEntityRepository users;
    private final UserAccessKill kill;

    public KillSessionsByRoleCommandHandler(IGRPUserEntityRepository users, UserAccessKill kill) {
        this.users = users;
        this.kill = kill;
    }

    /** @return how many users were killed */
    @IgrpCommandHandler
    public Integer handle(KillSessionsByRoleCommand command) {
        log.info("Killing the users of role {} in {}, reason {} by {}", command.getRoleCode(),
                command.getDepartmentCode(), command.getReason(), command.getKilledBy());
        return kill.kill(users.findUserIdsByRoleAndDepartment(command.getRoleCode(), command.getDepartmentCode()),
                command.getReason(), command.getKilledBy());
    }
}
