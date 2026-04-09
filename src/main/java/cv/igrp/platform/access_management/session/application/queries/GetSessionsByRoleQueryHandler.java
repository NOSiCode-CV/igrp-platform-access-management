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
 * Query handler responsible for retrieving sessions for users belonging to a specific role/department.
 *
 * <p>
 * This handler receives a {@link GetSessionsByRoleQuery} containing role code, department code, 
 * status filter, and pagination details, delegates to the {@link SessionManagementService} 
 * to retrieve the sessions, and returns a {@link Page} of {@link SessionResponseDTO} objects.
 * </p>
 *
 * @see GetSessionsByRoleQuery
 * @see SessionResponseDTO
 * @see SessionManagementService
 */
@Slf4j
@Component
public class GetSessionsByRoleQueryHandler implements QueryHandler<GetSessionsByRoleQuery, Page<SessionResponseDTO>> {

    private final SessionManagementService sessionManagementService;

    /**
     * Constructs the handler with the required dependencies.
     *
     * @param sessionManagementService the service used to retrieve session information
     */
    public GetSessionsByRoleQueryHandler(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Handles the query to retrieve sessions for users belonging to a specific role/department.
     *
     * @param query the {@link GetSessionsByRoleQuery} containing role, department, status, and pagination details
     * @return a {@link Page} containing {@link SessionResponseDTO} objects
     */
    @IgrpQueryHandler
    public Page<SessionResponseDTO> handle(GetSessionsByRoleQuery query) {
        log.debug("Handling GetSessionsByRoleQuery for role: {} in department: {} with status: {}", 
                query.getRoleCode(), query.getDepartmentCode(), query.getStatus());
        
        Page<SessionResponseDTO> sessions = sessionManagementService.listSessionsByRole(
                query.getRoleCode(),
                query.getDepartmentCode(),
                query.getStatus(),
                query.getPageable()
        );
        
        log.debug("Retrieved {} sessions for role: {} in department: {}", 
                sessions.getTotalElements(), query.getRoleCode(), query.getDepartmentCode());
        
        return sessions;
    }
}
