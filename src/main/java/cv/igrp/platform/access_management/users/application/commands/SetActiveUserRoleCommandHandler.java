package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;

import java.util.Optional;

@Component
public class SetActiveUserRoleCommandHandler implements CommandHandler<SetActiveUserRoleCommand, ResponseEntity<RoleDepartmentDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(SetActiveUserRoleCommandHandler.class);

    private final IGRPUserEntityRepository igrpUserRepository;
    private final RoleEntityRepository roleRepository;
    private final DepartmentEntityRepository departmentRepository;


    public SetActiveUserRoleCommandHandler(
            IGRPUserEntityRepository igrpUserRepository,
            RoleEntityRepository roleRepository,
            DepartmentEntityRepository departmentRepository
    ) {
        this.igrpUserRepository = igrpUserRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<RoleDepartmentDTO> handle(SetActiveUserRoleCommand command) {

        String userId = command.getId();

        logger.info("Setting current user active role with ID: {}", userId);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            logger.warn("No user found with ID: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserEntity user = optionalUser.get();

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(command.getRoledepartmentdto().departmentCode());

        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            logger.warn("Could not set active role for user with ID: {} because the department is inactive", userId);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_INACTIVE, department.getCode());
        }

        RoleEntity role = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, command.getRoledepartmentdto().roleCode());

        if (role.getStatus() != Status.ACTIVE) {
            logger.warn("Could not set active role for user with ID: {} because the role is inactive", userId);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ROLE_INACTIVE, userId);
        }

        user.setActiveRole(role);

        var savedUser = igrpUserRepository.save(user);

        RoleDepartmentDTO dto = new RoleDepartmentDTO(savedUser.getActiveRole().getCode(), savedUser.getActiveRole().getDepartment().getCode());
        logger.info("User ID : {} has active role: {} (Department: {})", userId, savedUser.getActiveRole().getCode(), savedUser.getActiveRole().getDepartment().getCode());

        return ResponseEntity.ok(dto);

    }

}