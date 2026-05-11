package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the user by {@code externalId} and delegates to
 * {@link SessionInvalidationService#invalidateUserSession(Integer, String)} so
 * every active session for that user is revoked. Returns {@code true} when the
 * user existed and the invalidation was attempted; {@code false} when the
 * external id resolves to no user (caller maps that to HTTP 404).
 */
@Slf4j
@Component
public class KillAllUserSessionsCommandHandler
        implements CommandHandler<KillAllUserSessionsCommand, Boolean> {

    private final SessionInvalidationService sessionInvalidationService;
    private final IGRPUserEntityRepository userRepository;

    public KillAllUserSessionsCommandHandler(SessionInvalidationService sessionInvalidationService,
                                             IGRPUserEntityRepository userRepository) {
        this.sessionInvalidationService = sessionInvalidationService;
        this.userRepository = userRepository;
    }

    @IgrpCommandHandler
    public Boolean handle(KillAllUserSessionsCommand command) {
        Optional<IGRPUserEntity> user = userRepository.findByExternalId(command.getUserExternalId());
        if (user.isEmpty()) {
            log.warn("Cannot logout-all — user not found for externalId={}", command.getUserExternalId());
            return false;
        }
        Integer internalId = user.get().getInternalId();
        log.info("Admin logout-all for user externalId={} (internalId={}) reason={} by={}",
                command.getUserExternalId(), internalId, command.getReason(), command.getKilledBy());
        sessionInvalidationService.invalidateUserSession(internalId, command.getReason());
        return true;
    }
}
