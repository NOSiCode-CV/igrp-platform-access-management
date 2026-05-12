package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;

import java.util.Optional;
import java.util.Set;
import cv.igrp.platform.access_management.shared.security.SubjectParser;

@Component
public class SetActiveCurrentUserRoleCommandHandler implements CommandHandler<SetActiveCurrentUserRoleCommand, ResponseEntity<RoleDepartmentDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(SetActiveCurrentUserRoleCommandHandler.class);

    private final IGRPUserEntityRepository igrpUserRepository;
    private final RoleEntityRepository roleRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final AuthenticationHelper authenticationHelper;
    private final SecurityAuditService auditService;
    private final EventPublisher eventPublisher;

    /**
     * Constructs the handler with necessary dependencies.
     *
     * @param igrpUserRepository   the repository to retrieve user data
     * @param roleRepository       the repository to retrieve role data
     * @param departmentRepository the repository to retrieve department data
     * @param authenticationHelper the helper to access the authenticated user context
     * @param auditService         the service for logging security audit events
     */
    public SetActiveCurrentUserRoleCommandHandler(
            IGRPUserEntityRepository igrpUserRepository,
            RoleEntityRepository roleRepository,
            DepartmentEntityRepository departmentRepository,
            AuthenticationHelper authenticationHelper,
            SecurityAuditService auditService,
            EventPublisher eventPublisher
    ) {
        this.igrpUserRepository = igrpUserRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.authenticationHelper = authenticationHelper;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @IgrpCommandHandler
    public ResponseEntity<RoleDepartmentDTO> handle(SetActiveCurrentUserRoleCommand command) {

        Integer userId;
        try {
            userId = SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
        } catch (NumberFormatException e) {
            logger.error("Invalid token sub: expected an integer ID but got '{}'", authenticationHelper.getSub());
            throw IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "Invalid Token", "Token sub must be an integer ID");
        }

        logger.info("Setting current user active role with id: {}", userId);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findByIdWithRolesAndPermissions(userId);
        if (optionalUser.isEmpty()) {
            logger.warn("No user found with id: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserEntity user = optionalUser.get();
        Integer oldRoleId = Optional.ofNullable(user.getActiveRole()).map(RoleEntity::getId).orElse(null);

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(command.getRoledepartmentdto().departmentCode());

        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            logger.warn("Could not set active role for user with sub: {} because the department is inactive", userId);
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "Department Inactive", "Could not set active role for user with sub <%s> because the department is inactive".formatted(userId));
        }

        RoleEntity role = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, command.getRoledepartmentdto().roleCode());

        if (role.getStatus() != Status.ACTIVE) {
            logger.warn("Could not set active role for user with sub: {} because the role is inactive", userId);
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "Department Inactive", "Could not set active role for user with sub <%s> because the role is inactive".formatted(userId));
        }

        user.setActiveRole(role);

        var savedUser = igrpUserRepository.save(user);

        // Audit the profile switch
        auditService.logProfileSwitch(oldRoleId, savedUser.getActiveRole().getId());

        eventPublisher.publishUserRoleChanged(new UserRoleChangedEvent(
                userId,
                Set.of(savedUser.getActiveRole().getCode()),
                savedUser.getActiveRole().getDepartment().getCode(),
                UserRoleChangedEvent.CHANGE_ACTIVE_ROLE_CHANGED,
                authenticationHelper.getSub()));

        RoleDepartmentDTO dto = new RoleDepartmentDTO(savedUser.getActiveRole().getCode(), savedUser.getActiveRole().getDepartment().getCode());
        logger.info("User ID : {} has active role: {} (Department: {})", userId, savedUser.getActiveRole().getCode(), savedUser.getActiveRole().getDepartment().getCode());

        return ResponseEntity.ok(dto);

    }

}