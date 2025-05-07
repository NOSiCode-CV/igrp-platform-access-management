package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.role.application.queries.queries.GetRolesQuery;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles the retrieval of all {@link Role} entities with status {@code ACTIVE} or {@code INACTIVE}.
 *
 * <p>
 * This query handler fetches roles from the repository and maps them to {@link RoleDTO} objects.
 * Only roles with status {@link Status#ACTIVE} or {@link Status#INACTIVE} are included.
 * </p>
 *
 * <p>
 * The resulting list of DTOs is returned in a {@link ResponseEntity} with HTTP status 200 (OK).
 * </p>
 */
@Slf4j
@Service
public class GetRolesQueryHandler implements QueryHandler<GetRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs a new instance of {@code GetRolesQueryHandler} with the required dependencies.
     *
     * @param roleRepository the repository used to fetch roles from the database
     * @param roleMapper     the mapper used to convert {@link Role} entities to {@link RoleDTO}
     */
    public GetRolesQueryHandler(RoleRepository roleRepository, RoleMapper roleMapper) {

        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the {@link GetRolesQuery} to retrieve all active or inactive roles.
     *
     * <ul>
     *     <li>Queries the repository for roles with {@link Status#ACTIVE} or {@link Status#INACTIVE}</li>
     *     <li>Maps each {@link Role} entity to a {@link RoleDTO}</li>
     *     <li>Returns the list wrapped in a {@link ResponseEntity} with status 200 (OK)</li>
     * </ul>
     *
     * @param query the query object (currently unused, but may include filters in the future)
     * @return a {@link ResponseEntity} containing the list of {@link RoleDTO}
     */
    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoleDTO>> handle(GetRolesQuery query) {
        log.info("Get Roles");
        Status active = Status.ACTIVE;
        Status inactive = Status.INACTIVE;
        List<Role> allRoles = roleRepository.findByStatusIn(List.of(active, inactive));
        List<RoleDTO> collectedRole = allRoles.stream()
                .map(roleMapper::mapToDto)
                .toList();
        return new ResponseEntity<>(collectedRole, HttpStatus.OK);
    }
}