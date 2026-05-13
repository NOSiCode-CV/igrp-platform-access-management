package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

@Component
public class GetCurrentUserPermissionsQueryHandler implements QueryHandler<GetCurrentUserPermissionsQuery, ResponseEntity<List<PermissionDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserPermissionsQueryHandler.class);

    private final IGRPUserEntityRepository userRepository;
    private final PermissionMapper permissionMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetCurrentUserPermissionsQueryHandler(
            IGRPUserEntityRepository userRepository,
            PermissionMapper permissionMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.userRepository = userRepository;
        this.permissionMapper = permissionMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(GetCurrentUserPermissionsQuery query) {

        String userId;
        try {
            userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid token sub: expected an integer ID but got '{}'", authenticationHelper.getSub());
            throw IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "Invalid Token", "Token sub must be an integer ID");
        }

        LOGGER.info("Fetching permissions for user id={}", userId);

        IGRPUserEntity user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(() -> {
                    LOGGER.warn("User not found with id={}", userId);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Invalid User",
                            "User not found with id: " + userId);
                });

        List<RoleEntity> roles = Optional.ofNullable(user.getRoles()).orElse(Collections.emptyList());

        LOGGER.info("User {} has {} roles loaded", userId, roles.size());
        roles.forEach(role -> LOGGER.info("Role: {} (status: {})", role.getCode(), role.getStatus()));

        String roleCode = query.getRoleCode();
        if (roleCode != null && !roleCode.isBlank()) {
            roles = roles.stream()
                    .filter(r -> Objects.equals(r.getCode(), roleCode))
                    .toList();
            LOGGER.info("Filtered to {} roles with code: {}", roles.size(), roleCode);
        }

        Set<PermissionEntity> permissions = roles.stream()
                .filter(Objects::nonNull)
                .map(RoleEntity::getPermissions)
                .filter(Objects::nonNull)
                .peek(perms -> LOGGER.info("Role has {} permissions", perms.size()))
                .flatMap(Collection::stream)
                .filter(p -> Objects.equals(p.getStatus(), Status.ACTIVE))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Map to DTOs and ensure no nulls and no duplicates by ID while preserving order
        Set<Integer> seenIds = new HashSet<>();
        List<PermissionDTO> result = permissions.stream()
                .map(permissionMapper::mapToDTO)
                .filter(Objects::nonNull)
                .filter(dto -> dto.getId() == null || seenIds.add(dto.getId()))
                .collect(Collectors.toList());

        LOGGER.info("User id={} has {} permission(s)", userId, result.size());

        return ResponseEntity.ok(result);
    }

}