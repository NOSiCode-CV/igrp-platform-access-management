package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.AddRolesToUserCommand;
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
        // Buscar o usuário usando o user_id do comando
        IGRPUser user = userRepository.findById(command.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));

        // Obter o papel (role) pelo role_id do comando
        Integer roleId = command.getRoleuserdto().role_id(); // Aqui acessamos o role_id do DTO
        Role roleToAdd = roleRepository.findById(roleId) // Buscando o papel pelo ID
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + roleId));

        // Adicionar o papel ao usuário
        if (user.getRoles() != null) {
            user.getRoles().add(roleToAdd);
        } else {
            user.setRoles(List.of(roleToAdd)); // Se não houver roles, cria uma nova lista com o role
        }

        // Salvar o usuário atualizado
        userRepository.save(user);

        // Mapeando o papel para RoleDTO
        RoleDTO roleDTO = roleMapper.mapToDto(roleToAdd);

        // Retornar a resposta com a lista de RoleDTO
        return ResponseEntity.status(201).body(List.of(roleDTO)); // Retorna um único RoleDTO, pois só foi adicionado um papel
    }
}