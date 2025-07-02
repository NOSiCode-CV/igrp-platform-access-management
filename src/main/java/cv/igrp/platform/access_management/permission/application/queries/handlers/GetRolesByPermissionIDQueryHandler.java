package cv.igrp.platform.access_management.permission.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission.application.queries.queries.GetRolesByPermissionIDQuery;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Query handler responsible for retrieving all active or inactive {@link RoleDTO} instances
 * associated with a given {@link Permission} ID.
 *
 * <p>This handler:
 * <ul>
 *   <li>Fetches the {@link Permission} by its ID, ensuring it is not marked as {@link Status#DELETED}.</li>
 *   <li>Extracts the related {@link Role}s and filters only those with status {@link Status#ACTIVE} or {@link Status#INACTIVE}.</li>
 *   <li>Maps the filtered roles to their corresponding {@link RoleDTO} representations.</li>
 * </ul>
 *
 * <p>If the permission is not found or is marked as {@link Status#DELETED}, a {@link IgrpResponseStatusException}
 * with {@link HttpStatus#NOT_FOUND} is thrown.
 *
 * @see GetRolesByPermissionIDQuery
 * @see PermissionRepository
 * @see RoleMapper
 * @see RoleDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Service
public class GetRolesByPermissionIDQueryHandler implements QueryHandler<GetRolesByPermissionIDQuery, ResponseEntity<List<RoleDTO>>> {

    private final RoleMapper roleMapper;
    private final PermissionRepository permissionRepository;

    /**
     * Constructs a new instance of the handler with required dependencies.
     *
     * @param roleMapper           Mapper to convert {@link Role} entities to {@link RoleDTO}.
     * @param permissionRepository Repository used to retrieve {@link Permission} entities.
     */
    public GetRolesByPermissionIDQueryHandler(RoleMapper roleMapper, PermissionRepository permissionRepository) {

        this.roleMapper = roleMapper;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Handles the query by retrieving and returning all non-deleted roles associated with
     * the given permission ID.
     *
     * @param query the query containing the permission ID.
     * @return a {@link ResponseEntity} containing a list of {@link RoleDTO}s.
     * @throws IgrpResponseStatusException if the permission is not found or is deleted.
     */
    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoleDTO>> handle(GetRolesByPermissionIDQuery query) {
        log.info("Get Roles with Permission ID {}", query.getId());
        Permission permissionFound = permissionRepository.findById(query.getId())
                .filter(permission -> permission.getStatus().equals(Status.ACTIVE) || permission.getStatus().equals(Status.INACTIVE))
                .orElseThrow(() -> {
                    log.warn("Get Roles with Permission ID {}", query.getId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Get Role By Permission ID", "Permission with id: " + query.getId() + " not found."
                    );
                });
        Set<Role> roles = Optional.ofNullable(permissionFound.getRoles())
                .orElse(Collections.emptySet());

        List<RoleDTO> response = roles
                .stream()
                .filter(role -> role.getStatus().equals(Status.ACTIVE) || role.getStatus().equals(Status.INACTIVE))
                .map(roleMapper::mapToDto)
                .toList();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}