package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Component
public class GetCurrentUserDepartmentRolesQueryHandler implements QueryHandler<GetCurrentUserDepartmentRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserDepartmentRolesQueryHandler.class);

    private final RoleEntityRepository roleRepository;
    private final IGRPUserEntityRepository userRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final RoleMapper roleMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetCurrentUserDepartmentRolesQueryHandler(RoleEntityRepository roleRepository, IGRPUserEntityRepository userRepository, DepartmentEntityRepository departmentRepository, RoleMapper roleMapper, AuthenticationHelper authenticationHelper) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleMapper = roleMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetCurrentUserDepartmentRolesQuery query) {

        var user = userRepository.findByIdWithRolesAndPermissions(SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub())).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with external ID: " + authenticationHelper.getSub() + " not found in database."
                )
        );

        LOGGER.info("Getting roles for user: {}", user.getExternalId());

        var department = departmentRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

        // Role-based superadmin check — see GetCurrentUserApplicationsQueryHandler
        // for full rationale. Same pattern as PermissionCacheService.isSuperAdmin.
        boolean isSuperAdmin = user.getRoles() != null && user.getRoles().stream()
                .map(r -> r != null ? r.getCode() : null)
                .filter(Objects::nonNull)
                .anyMatch(SUPER_ADMIN_ROLE::equals);

        List<RoleDTO> roles = isSuperAdmin ?
                roleRepository.findAllByDepartmentAndStatusNotDeleted(department)
                        .stream()
                        .map(roleMapper::mapToDto)
                        .toList()
                : roleRepository.findByDepartmentIdAndCurrentUserIdAndStatusNotDeleted(user, department)
                .stream()
                .map(roleMapper::mapToDto)
                .toList();

        return ResponseEntity.ok(roles);

    }

}