package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

/**
 * Handler responsible for processing the {@link GetCurrentUserRolesQuery} and retrieving the list of roles assigned to the current authenticated user.
 * <p>
 * This handler performs the following steps:
 * <ul>
 *     <li>Fetches the user entity by the current user's provider ID.</li>
 *     <li>If the user is not found, throws {@link IgrpResponseStatusException}.</li>
 *     <li>Retrieves the user's roles and maps them to {@link RoleDTO} instances using {@link RoleMapper}.</li>
 *     <li>Filters out any {@code null} values returned by the mapper to ensure a clean result.</li>
 *     <li>Returns the resulting list of {@link RoleDTO} wrapped in a {@link ResponseEntity} with HTTP status 200 (OK).</li>
 * </ul>
 *
 * @see GetCurrentUserRolesQuery
 * @see RoleDTO
 * @see IGRPUserEntity
 * @see IGRPUserEntityRepository
 * @see RoleMapper
 */
@Component
public class GetCurrentUserRolesQueryHandler implements QueryHandler<GetCurrentUserRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserRolesQueryHandler.class);

    private final IGRPUserEntityRepository userRepository;
    private final RoleMapper roleMapper;
    private final AuthenticationHelper authenticationHelper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param userRepository the repository used to retrieve user data
     * @param roleMapper     the mapper used to convert {@link RoleEntity} entities to {@link RoleDTO}
     */

    public GetCurrentUserRolesQueryHandler(
            IGRPUserEntityRepository userRepository,
            RoleMapper roleMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetCurrentUserRolesQuery query) {

        Integer userId;
        try {
            userId = Integer.parseInt(authenticationHelper.getSub());
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid token sub: expected an integer ID but got '{}'", authenticationHelper.getSub());
            throw IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "Invalid Token", "Token sub must be an integer ID");
        }

        LOGGER.info("Fetching roles for user ID={}", userId);

        IGRPUserEntity user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Invalid User", "User not found with id: " + userId));

        List<RoleDTO> result = user.getUserRoleAssignments().stream()
                .filter(ura -> ura.getExpiresAt() == null || ura.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                .filter(ura -> Objects.equals(ura.getRole().getStatus(), Status.ACTIVE))
                .map(roleMapper::mapToDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.info("User {} has {} roles loaded", userId, result.size());

        return ResponseEntity.ok(result);

    }

}