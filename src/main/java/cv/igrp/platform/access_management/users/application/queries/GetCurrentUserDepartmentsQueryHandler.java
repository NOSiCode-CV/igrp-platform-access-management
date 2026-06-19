package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
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

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Component
public class GetCurrentUserDepartmentsQueryHandler implements QueryHandler<GetCurrentUserDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserDepartmentsQueryHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final IGRPUserEntityRepository userRepository;
    private final DepartmentMapper departmentMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetCurrentUserDepartmentsQueryHandler(
            DepartmentEntityRepository departmentRepository,
            IGRPUserEntityRepository userRepository,
            DepartmentMapper departmentMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.departmentMapper = departmentMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<DepartmentDTO>> handle(GetCurrentUserDepartmentsQuery query) {

        var user = userRepository.findByIdWithRolesAndPermissions(SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub())).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with external ID: " + authenticationHelper.getSub() + " not found in database."
                )
        );

        LOGGER.info("Getting departments for user: {}", user.getExternalId());

        // Role-based superadmin check — see GetCurrentUserApplicationsQueryHandler
        // for full rationale. Same pattern as PermissionCacheService.isSuperAdmin:
        // user is superadmin iff any of their assigned roles has the canonical
        // code ConfigurationService.SUPER_ADMIN_ROLE ("DEPT_IGRP.superadmin").
        boolean isSuperAdmin = user.getRoles() != null && user.getRoles().stream()
                .map(r -> r != null ? r.getCode() : null)
                .filter(Objects::nonNull)
                .anyMatch(SUPER_ADMIN_ROLE::equals);

        List<DepartmentDTO> departments = isSuperAdmin ?
                departmentRepository.findAllActiveFiltered(query.getDepartmentCode())
                        .stream()
                        .map(departmentMapper::toDto)
                        .toList()
                : departmentRepository.findByCurrentUserAndNotDeletedFiltered(user.getId(), query.getDepartmentCode())
                .stream()
                .map(departmentMapper::toDto)
                .toList();

        return ResponseEntity.ok(departments);

    }

}