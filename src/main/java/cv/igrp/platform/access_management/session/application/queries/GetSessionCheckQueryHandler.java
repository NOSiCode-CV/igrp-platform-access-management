package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionCheckResponseDTO;
import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Resolves the {@code SessionEntity} bound to the JWT {@code sid} claim and
 * synthesizes the response payload consumed by FE polling. The enforcement
 * filter exempts {@code /api/session/check} so that revoked-session JWTs can
 * still reach this endpoint and receive an informative {@code valid=false}
 * answer instead of an opaque 401.
 */
@Slf4j
@Component
public class GetSessionCheckQueryHandler implements QueryHandler<GetSessionCheckQuery, SessionCheckResponseDTO> {

    private final SessionRepository sessionRepository;
    private final SessionProperties sessionProperties;

    public GetSessionCheckQueryHandler(SessionRepository sessionRepository,
                                       SessionProperties sessionProperties) {
        this.sessionRepository = sessionRepository;
        this.sessionProperties = sessionProperties;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public SessionCheckResponseDTO handle(GetSessionCheckQuery query) {
        SessionCheckResponseDTO response = new SessionCheckResponseDTO();
        response.setSid(query.getSid());
        response.setSub(query.getSub());

        if (query.getSid() == null) {
            response.setValid(false);
            response.setReason("MISSING_SID");
            return response;
        }

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionId(query.getSid());
        if (sessionOpt.isEmpty()) {
            response.setValid(false);
            response.setReason("SESSION_NOT_FOUND");
            return response;
        }

        SessionEntity session = sessionOpt.get();
        response.setExpiresAt(session.getExpiresAt());
        response.setLastSeenAt(session.getLastSeenAt());
        response.setAbsoluteExpiryAt(session.getAbsoluteExpiresAt());
        response.setIdleTimeoutAt(computeIdleTimeoutAt(session));

        Instant now = Instant.now();
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            response.setValid(false);
            response.setReason(session.getClosedReason() != null
                    ? session.getClosedReason()
                    : session.getStatus().name());
            return response;
        }
        if (session.getAbsoluteExpiresAt() != null && !now.isBefore(session.getAbsoluteExpiresAt())) {
            response.setValid(false);
            response.setReason("ABSOLUTE_TIMEOUT");
            return response;
        }
        if (session.getExpiresAt() != null && !now.isBefore(session.getExpiresAt())) {
            response.setValid(false);
            response.setReason("IDLE_TIMEOUT");
            return response;
        }

        response.setValid(true);
        return response;
    }

    private Instant computeIdleTimeoutAt(SessionEntity session) {
        if (session.getLastSeenAt() == null) {
            return null;
        }
        return session.getLastSeenAt().plusSeconds(sessionProperties.getTimeoutSeconds());
    }
}
