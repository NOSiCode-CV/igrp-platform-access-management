package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.UserRepository;
import cv.igrp.platform.access_management.shared.domain.models.User;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.app.application.dto.RoleDTO;
import cv.igrp.platform.access_management.app.application.commands.commands.AddRolesToUserCommand;
import cv.igrp.platform.access_management.app.mapper.RoleMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddRolesToUserCommandHandler implements CommandHandler<AddRolesToUserCommand, ResponseEntity<List<RoleDTO>>> {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public AddRolesToUserCommandHandler(UserRepository userRepository, RoleRepository roleRepository, RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(AddRolesToUserCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getUserId()));

        List<Role> rolesToAdd = roleRepository.findAllById(command.getRoleIds());
        user.getRoles().addAll(rolesToAdd);
        userRepository.save(user);

        List<RoleDTO> result = user.getRoles().stream().map(roleMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.status(201).body(result);
    }
}