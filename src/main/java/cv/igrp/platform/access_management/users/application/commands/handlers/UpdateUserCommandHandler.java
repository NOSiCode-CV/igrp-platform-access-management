package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;

import java.util.List;

import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.commands.commands.UpdateUserCommand;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;

@Service
public class UpdateUserCommandHandler implements CommandHandler<UpdateUserCommand, ResponseEntity<IGRPUserDTO>> {

    private final IGRPUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final IGRPUserMapper userMapper;

    public UpdateUserCommandHandler(IGRPUserRepository userRepository, RoleRepository roleRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<IGRPUserDTO> handle(UpdateUserCommand command) {
        IGRPUserDTO dto = command.getIgrpuserdto();

        // Verifica se o user existe
        IGRPUser user = userRepository.findById(command.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));

        // Atualiza os campos
        user.setName(dto.getName());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());

        // Salva o user atualizado
        var updatedUser = userRepository.save(user);

        // Retorna o DTO atualizado
        return ResponseEntity.ok(userMapper.toDto(updatedUser));
    }
}