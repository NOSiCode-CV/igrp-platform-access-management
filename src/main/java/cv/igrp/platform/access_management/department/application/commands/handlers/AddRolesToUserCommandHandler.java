package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import cv.igrp.platform.access_management.users.application.commands.commands.AddRolesToUserCommand;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
public class AddRolesToUserCommandHandler implements CommandHandler<AddRolesToUserCommand, ResponseEntity<List<RoleDTO>>> {

    private final IGRPUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public AddRolesToUserCommandHandler(IGRPUserRepository userRepository, RoleRepository roleRepository, RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(AddRolesToUserCommand command) {
        // Buscar o usuário
        IGRPUser user = userRepository.findById(command.getId().toString()) // Certifique-se de que o ID é String
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));

        // Buscar o papel (role) correspondente ao role_id
        Role roleToAdd = roleRepository.findById(command.getRoleuserdto().role_id())
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + command.getRoleuserdto().role_id()));

        // Adicionar o papel ao usuário
        if (user.getRoles() != null) {
            user.getRoles().add(roleToAdd);
        } else {
            user.setRoles(List.of(roleToAdd)); // Caso o usuário não tenha papéis definidos
        }

        userRepository.save(user);

        // Mapeando o papel para RoleDTO
        RoleDTO result = roleMapper.mapToDto(roleToAdd);

        // Retornar a resposta com o RoleDTO
        return ResponseEntity.status(201).body(List.of(result));
    }
}
