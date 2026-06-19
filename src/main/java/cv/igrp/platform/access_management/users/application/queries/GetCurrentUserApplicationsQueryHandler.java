package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
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

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Component
public class GetCurrentUserApplicationsQueryHandler implements QueryHandler<GetCurrentUserApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserApplicationsQueryHandler.class);

    private final ApplicationEntityRepository applicationRepository;
    private final IGRPUserEntityRepository userRepository;
    private final ApplicationMapper applicationMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetCurrentUserApplicationsQueryHandler(
            ApplicationEntityRepository applicationRepository,
            IGRPUserEntityRepository userRepository,
            ApplicationMapper applicationMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.applicationMapper = applicationMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<ApplicationDTO>> handle(GetCurrentUserApplicationsQuery query) {

        var user = userRepository.findByIdWithRolesAndPermissions(SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub())).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with external ID: " + authenticationHelper.getSub() + " not found in database."
                )
        );

        LOGGER.info("Getting applications for user: {}", user.getEmail());

        // Role-based superadmin check (replaces the previous username/external-id
        // equality test against igrp.superadmin.user-external-id). Same pattern as
        // PermissionCacheService.isSuperAdmin: the user is treated as superadmin
        // when ANY of their assigned roles has the well-known code
        // ConfigurationService.SUPER_ADMIN_ROLE ("DEPT_IGRP.superadmin").
        //
        // Why role-based is preferable here:
        //   - Decoupled from a single hard-coded user identity — any user the
        //     admin grants the superadmin role gets the full-catalogue view,
        //     instead of having to rename / re-provision the bootstrap account.
        //   - Survives the migration to UUID-based subjects (the external-id
        //     property was inherited from the username era).
        //   - Aligned with how every other "is this caller privileged?" check
        //     in the codebase already works.
        boolean isSuperAdmin = user.getRoles() != null && user.getRoles().stream()
                .map(r -> r != null ? r.getCode() : null)
                .filter(Objects::nonNull)
                .anyMatch(SUPER_ADMIN_ROLE::equals);

        List<ApplicationDTO> applications =
                isSuperAdmin
                        ? applicationRepository
                        .findAllActiveFiltered(
                                query.getApplicationCode(),
                                query.getApplicationName()
                        )
                        .stream()
                        .map(applicationMapper::toDto)
                        .toList()
                        : applicationRepository
                        .findByCurrentUserAndActiveFiltered(
                                user.getId(),
                                query.getApplicationCode(),
                                query.getApplicationName()
                        )
                        .stream()
                        .map(applicationMapper::toDto)
                        .toList();

        return ResponseEntity.ok(applications);

    }

}