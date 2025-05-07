package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.queries.queries.GetPermissionsByRoleIdQuery;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles the query to retrieve permissions associated with a specific role ID.
 * <p>
 * This query handler performs the following:
 * <ul>
 *   <li>Validates if the role with the given ID exists and is not marked as {@link Status#DELETED}.</li>
 *   <li>Filters out any permissions with {@link Status#DELETED}.</li>
 *   <li>Maps the valid permissions to {@link PermissionDTO} objects.</li>
 *   <li>Returns the permissions in a {@link ResponseEntity} with status {@code 200 OK}.</li>
 * </ul>
 */
@Slf4j
@Service
public class GetPermissionsByRoleIdQueryHandler implements QueryHandler<GetPermissionsByRoleIdQuery, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Constructs the handler with necessary dependencies.
     *
     * @param roleRepository   repository to retrieve roles from the database
     * @param permissionMapper mapper to convert permission entities to DTOs
     */
    public GetPermissionsByRoleIdQueryHandler(RoleRepository roleRepository, PermissionMapper permissionMapper) {

        this.roleRepository = roleRepository;
        this.permissionMapper = permissionMapper;
    }

    /**
     * Handles the query to fetch permissions for a given role ID.
     *
     * @param query the query containing the role ID
     * @return a list of mapped {@link PermissionDTO} objects excluding those with status {@link Status#DELETED}
     * @throws IgrpResponseStatusException if the role is not found or is marked as deleted
     */
    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<PermissionDTO>> handle(GetPermissionsByRoleIdQuery query) {
        log.info("Get Permissions from Role with id {}.", query.getId());
        Role foundRole = roleRepository.findByIdAndStatusNot(query.getId(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with id {} not found.", query.getId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Permission By Role ID", "Role with id: " + query.getId() + " not found.")
                    );
                });
        List<PermissionDTO> permissionList = foundRole.getPermissions()
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .map(permissionMapper::mapToDTO)
                .toList();
        return new ResponseEntity<>(permissionList, HttpStatus.OK);
    }
}