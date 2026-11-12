package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.UserAccessKill;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Kills the user behind one session (#19): kill is always the whole platform,
 * so every session, authorization and permission key of that user goes, not
 * only the session named.
 *
 * @see UserAccessKill
 */
@Slf4j
@Component
public class KillSessionCommandHandler implements CommandHandler<KillSessionCommand, Boolean> {

    private final SessionRepository sessions;
    private final UserAccessKill kill;

    public KillSessionCommandHandler(SessionRepository sessions, UserAccessKill kill) {
        this.sessions = sessions;
        this.kill = kill;
    }

    /** @return {@code false} when no session has that id */
    @IgrpCommandHandler
    public Boolean handle(KillSessionCommand command) {
        Optional<SessionEntity> session = sessions.findBySessionId(command.getSessionId());
        if (session.isEmpty()) {
            log.debug("Session {} not found", command.getSessionId());
            return false;
        }
        log.info("Killing the user of session {} on the whole platform, reason {} by {}",
                command.getSessionId(), command.getReason(), command.getKilledBy());
        kill.kill(Set.of(session.get().getUserId()), command.getReason(), command.getKilledBy());
        return true;
    }
}
