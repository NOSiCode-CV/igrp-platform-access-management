package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Query handler responsible for retrieving the current session for a specific user.
 *
 * <p>
 * This handler receives a {@link GetUserSessionQuery} containing the user's external ID,
 * delegates to the {@link SessionManagementService} to fetch the user's session,
 * and returns an {@link Optional} containing the session if found.
 * </p>
 *
 * @see GetUserSessionQuery
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class GetUserSessionQueryHandler implements QueryHandler<GetUserSessionQuery, Optional<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to retrieve session information
     */
    public GetUserSessionQueryHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the query to retrieve the current session for a specific user.
     *
     * @param query the {@link GetUserSessionQuery} containing the user's external ID
     * @return an {@link Optional} containing the {@link SessionResponseDTO} if an active session exists
     */
    @IgrpQueryHandler
    public Optional<SessionResponseDTO> handle(GetUserSessionQuery query) {
        log.debug("Handling GetUserSessionQuery for user: {}", query.getUserId());
        
        Optional<SessionResponseDTO> session = sessionManagementService.getCurrentSession(query.getUserId());
        
        if (session.isPresent()) {
            log.debug("User session found for user: {}", query.getUserId());
        } else {
            log.debug("No active session found for user: {}", query.getUserId());
        }
        
        return session;
    }
}
