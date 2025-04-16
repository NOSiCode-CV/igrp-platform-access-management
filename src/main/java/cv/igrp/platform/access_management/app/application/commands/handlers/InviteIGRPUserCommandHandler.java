package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.UserDTO;
import cv.igrp.platform.access_management.app.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.app.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.app.mapper.UserMapper;
import cv.igrp.platform.access_management.app.application.commands.commands.CreateDepartmentCommand;
import cv.igrp.platform.access_management.app.application.commands.commands.InviteIGRPUserCommand;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

// INVITE IGRP USER TO DEPARTMENT
@Service
public class InviteIGRPUserCommandHandler implements CommandHandler<InviteIGRPUserCommand, ResponseEntity<Void>> {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final DepartmentRepository departmentRepository;

    public InviteIGRPUserCommandHandler(UserRepository userRepository, UserMapper userMapper, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<Void> handle(InviteIGRPUserCommand command) {
        Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getDepartmentId()));

        UserDTO user = userMapper.toEntity(command.getIgrpUserDTO());
        user.setDepartment(department);
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }
}