package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;

import java.util.ArrayList;
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

    public CreateUserCommandHandler(IGRPUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<?> handle(CreateUserCommand command) {
        IGRPUser user = new IGRPUser();
        user.setName(command.getName());
        user.setUsername(command.getUsername());
        user.setEmail(command.getEmail());
        user.setRoles(new ArrayList<>()); // Nenhum papel atribuído no momento

        userRepository.save(user);

        return ResponseEntity.status(201).body("Usuário criado com sucesso!");
    }
}






