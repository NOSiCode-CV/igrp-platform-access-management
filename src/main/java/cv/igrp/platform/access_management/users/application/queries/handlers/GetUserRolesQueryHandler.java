package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUserRolesQuery;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handler responsible for processing the {@link GetUserRolesQuery} and retrieving the list of roles assigned to a specific user.
 * <p>
 * This handler performs the following steps:
 * <ul>
 *     <li>Fetches the user entity by the provided user ID.</li>
 *     <li>If the user is not found, throws {@link jakarta.persistence.EntityNotFoundException}.</li>
 *     <li>Retrieves the user's roles and maps them to {@link RoleDTO} instances using {@link RoleMapper}.</li>
 *     <li>Filters out any {@code null} values returned by the mapper to ensure a clean result.</li>
 *     <li>Returns the resulting list of {@link RoleDTO} wrapped in a {@link ResponseEntity} with HTTP status 200 (OK).</li>
 * </ul>
 *
 * @see GetUserRolesQuery
 * @see RoleDTO
 * @see IGRPUser
 * @see IGRPUserRepository
 * @see RoleMapper
 */
@Service
public class GetUserRolesQueryHandler implements QueryHandler<GetUserRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private final IGRPUserRepository userRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param userRepository the repository used to retrieve user data
     * @param roleMapper     the mapper used to convert {@link Role} entities to {@link RoleDTO}
     */
    public GetUserRolesQueryHandler(IGRPUserRepository userRepository, RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the {@link GetUserRolesQuery} by retrieving and returning the roles of the specified user.
     *
     * @param query the query containing the user ID
     * @return a {@link ResponseEntity} containing the list of {@link RoleDTO}, or an empty list if the user has no roles
     * @throws EntityNotFoundException if the user with the specified ID is not found
     */
    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetUserRolesQuery query) {
        IGRPUser user = userRepository.findById(query.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + query.getId()));

        List<Role> roles = Optional.ofNullable(user.getRoles()).orElse(Collections.emptyList());

        List<RoleDTO> result = roles.stream()
                .map(roleMapper::mapToDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
