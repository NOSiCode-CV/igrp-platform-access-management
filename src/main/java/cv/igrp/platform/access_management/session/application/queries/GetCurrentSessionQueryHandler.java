package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Query handler responsible for retrieving the current active session for a user.
 *
 * <p>
 * This handler receives a {@link GetCurrentSessionQuery} containing the user's external ID,
 * delegates to the {@link SessionManagementService} to fetch the current session,
 * and returns an {@link Optional} containing the session if found.
 * </p>
 *
 * @see GetCurrentSessionQuery
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class GetCurrentSessionQueryHandler implements QueryHandler<GetCurrentSessionQuery, Optional<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to retrieve session information
     */
    public GetCurrentSessionQueryHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the query to retrieve the current session for a user.
     *
     * @param query the {@link GetCurrentSessionQuery} containing the user's external ID
     * @return an {@link Optional} containing the {@link SessionResponseDTO} if an active session exists
     */
    @IgrpQueryHandler
    public Optional<SessionResponseDTO> handle(GetCurrentSessionQuery query) {
        log.debug("Handling GetCurrentSessionQuery for user: {}", query.getUserExternalId());
        
        Optional<SessionResponseDTO> session = sessionManagementService.getCurrentSession(query.getUserExternalId());
        
        if (session.isPresent()) {
            log.debug("Current session found for user: {}", query.getUserExternalId());
        } else {
            log.debug("No active session found for user: {}", query.getUserExternalId());
        }
        
        return session;
    }
}
