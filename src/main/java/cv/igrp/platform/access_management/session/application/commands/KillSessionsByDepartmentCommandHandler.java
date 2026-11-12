package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.UserAccessKill;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Kills every user with a role in a department (#19), each on the whole
 * platform.
 *
 * @see UserAccessKill
 */
@Slf4j
@Component
public class KillSessionsByDepartmentCommandHandler
        implements CommandHandler<KillSessionsByDepartmentCommand, Integer> {

    private final IGRPUserEntityRepository users;
    private final UserAccessKill kill;

    public KillSessionsByDepartmentCommandHandler(IGRPUserEntityRepository users, UserAccessKill kill) {
        this.users = users;
        this.kill = kill;
    }

    /** @return how many users were killed */
    @IgrpCommandHandler
    public Integer handle(KillSessionsByDepartmentCommand command) {
        log.info("Killing the users of department {}, reason {} by {}", command.getDepartmentCode(),
                command.getReason(), command.getKilledBy());
        return kill.kill(users.findUserIdsByDepartment(command.getDepartmentCode()), command.getReason(),
                command.getKilledBy());
    }
}
