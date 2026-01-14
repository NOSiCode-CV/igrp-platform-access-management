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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class GetUserPermissionsQueryHandler implements QueryHandler<GetUserPermissionsQuery, ResponseEntity<List<PermissionDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserPermissionsQueryHandler.class);

    private final IGRPUserEntityRepository userRepository;
    private final PermissionMapper permissionMapper;

    public GetUserPermissionsQueryHandler(IGRPUserEntityRepository userRepository, PermissionMapper permissionMapper) {
        this.userRepository = userRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(GetUserPermissionsQuery query) {

        Integer id = query.getId();
        LOGGER.info("Fetching permissions for user ID={}", id);

        IGRPUserEntity user = userRepository.findById(id)
                .orElseThrow(() -> {
                    LOGGER.warn("User not found with ID ={}", id);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Invalid User",
                            "User not found with ID: " + id);
                });

        List<RoleEntity> roles = Optional.ofNullable(user.getRoles()).orElse(Collections.emptyList());

        String roleCode = query.getRoleCode();
        if (roleCode != null && !roleCode.isBlank()) {
            roles = roles.stream()
                    .filter(r -> Objects.equals(r.getCode(), roleCode))
                    .toList();
        }

        Set<PermissionEntity> permissions = roles.stream()
                .filter(Objects::nonNull)
                .map(RoleEntity::getPermissions)
                .filter(Objects::nonNull)
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

        LOGGER.info("User ID={} has {} permission(s)", id, result.size());

        return ResponseEntity.ok(result);

    }

}