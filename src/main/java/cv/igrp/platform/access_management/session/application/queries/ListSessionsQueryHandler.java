package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Query handler responsible for listing sessions with optional status filtering and pagination.
 *
 * <p>
 * This handler receives a {@link ListSessionsQuery} containing status filter and pagination details,
 * delegates to the {@link SessionManagementService} to retrieve the sessions,
 * and returns a {@link Page} of {@link SessionResponseDTO} objects.
 * </p>
 *
 * @see ListSessionsQuery
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class ListSessionsQueryHandler implements QueryHandler<ListSessionsQuery, Page<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to retrieve session information
     */
    public ListSessionsQueryHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the query to list sessions with optional status filtering and pagination.
     *
     * @param query the {@link ListSessionsQuery} containing status filter and pagination details
     * @return a {@link Page} containing {@link SessionResponseDTO} objects
     */
    @IgrpQueryHandler
    public Page<SessionResponseDTO> handle(ListSessionsQuery query) {
        log.debug("Handling ListSessionsQuery with status: {} and pageable: {}", 
                query.getStatus(), query.getPageable());
        
        Page<SessionResponseDTO> sessions = sessionManagementService.listSessions(
                query.getStatus(),
                query.getPageable()
        );
        
        log.debug("Retrieved {} sessions with status: {}", 
                sessions.getTotalElements(), query.getStatus());
        
        return sessions;
    }
}
