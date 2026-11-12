package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.UserAccessKill;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Logout-all for one user, by an administrator: a kill on the whole platform
 * (#19).
 *
 * @see UserAccessKill
 */
@Slf4j
@Component
public class KillAllUserSessionsCommandHandler implements CommandHandler<KillAllUserSessionsCommand, Boolean> {

    private final UserAccessKill kill;
    private final IGRPUserEntityRepository userRepository;

    public KillAllUserSessionsCommandHandler(UserAccessKill kill, IGRPUserEntityRepository userRepository) {
        this.kill = kill;
        this.userRepository = userRepository;
    }

    /** @return {@code false} when the user does not exist */
    @IgrpCommandHandler
    public Boolean handle(KillAllUserSessionsCommand command) {
        Optional<IGRPUserEntity> user = userRepository.findById(command.getUserExternalId());
        if (user.isEmpty()) {
            log.warn("Cannot logout-all — user not found for externalId={}", command.getUserExternalId());
            return false;
        }
        log.info("Admin logout-all for user {} reason={} by={}",
                user.get().getInternalId(), command.getReason(), command.getKilledBy());
        kill.kill(Set.of(user.get().getInternalId()), command.getReason(), command.getKilledBy());
        return true;
    }
}
