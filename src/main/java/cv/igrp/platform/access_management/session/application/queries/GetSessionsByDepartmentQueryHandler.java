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
 * Query handler responsible for retrieving sessions for users belonging to a specific department.
 *
 * <p>
 * This handler receives a {@link GetSessionsByDepartmentQuery} containing department code, 
 * status filter, and pagination details, delegates to the {@link SessionManagementService} 
 * to retrieve the sessions, and returns a {@link Page} of {@link SessionResponseDTO} objects.
 * </p>
 *
 * @see GetSessionsByDepartmentQuery
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class GetSessionsByDepartmentQueryHandler implements QueryHandler<GetSessionsByDepartmentQuery, Page<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to retrieve session information
     */
    public GetSessionsByDepartmentQueryHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the query to retrieve sessions for users belonging to a specific department.
     *
     * @param query the {@link GetSessionsByDepartmentQuery} containing department, status, and pagination details
     * @return a {@link Page} containing {@link SessionResponseDTO} objects
     */
    @IgrpQueryHandler
    public Page<SessionResponseDTO> handle(GetSessionsByDepartmentQuery query) {
        log.debug("Handling GetSessionsByDepartmentQuery for department: {} with status: {}", 
                query.getDepartmentCode(), query.getStatus());
        
        Page<SessionResponseDTO> sessions = sessionManagementService.listSessionsByDepartment(
                query.getDepartmentCode(),
                query.getStatus(),
                query.getPageable()
        );
        
        log.debug("Retrieved {} sessions for department: {}", 
                sessions.getTotalElements(), query.getDepartmentCode());
        
        return sessions;
    }
}
