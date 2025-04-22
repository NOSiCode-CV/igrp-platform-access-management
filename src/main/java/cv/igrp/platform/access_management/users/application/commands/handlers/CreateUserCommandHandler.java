package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;

import java.util.ArrayList;

import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.commands.commands.CreateUserCommand;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;

@Service
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, ResponseEntity<?>> {

    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    public CreateUserCommandHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<IGRPUserDTO> handle(CreateUserCommand command) {
        IGRPUser user = new IGRPUser();
        user.setName(command.getIgrpuserdto().getName());
        user.setUsername(command.getIgrpuserdto().getUsername());
        user.setEmail(command.getIgrpuserdto().getEmail());
        user.setRoles(new ArrayList<>()); // Nenhum papel atribuído no momento

        var savedUser = userRepository.save(user);

        return ResponseEntity.ok(userMapper.toDto(savedUser));
    }
}






