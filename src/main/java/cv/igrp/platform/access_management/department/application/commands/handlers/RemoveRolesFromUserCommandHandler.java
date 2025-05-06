package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.users.application.commands.commands.RemoveRolesFromUserCommand;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

    private static final Logger logger = LoggerFactory.getLogger(RemoveRolesFromUserCommandHandler.class);

    private final IGRPUserRepository userRepository;
    private final RoleMapper roleMapper;

    public RemoveRolesFromUserCommandHandler(IGRPUserRepository userRepository, RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {
        logger.info("Handling RemoveRolesFromUserCommand: userId={}, roleIds={}", command.getId(), command.getRemoveRolesFromUserRequest());

        // Buscar o usuário
        IGRPUser user = userRepository.findById(command.getId())
                .orElseThrow(() -> {
                    logger.warn("User not found with id: {}", command.getId());
                    return new EntityNotFoundException("User not found with id: " + command.getId());
                });

        List<Integer> roleIdsToRemove = command.getRemoveRolesFromUserRequest();

        if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {
            logger.info("Removing roles with IDs {} from user {}", roleIdsToRemove, user.getId());
            user.getRoles().removeIf(role -> roleIdsToRemove.contains(role.getId()));
        } else {
            logger.warn("No roleIds provided to remove for user {}", user.getId());
        }

        // Salvar alterações
        userRepository.save(user);
        logger.info("User {} updated successfully", user.getId());

        // Mapear roles restantes para DTO
        List<RoleDTO> result = user.getRoles().stream()
                .map(roleMapper::mapToDto)
                .collect(Collectors.toList());

        logger.info("Returning {} remaining roles for user {}", result.size(), user.getId());
        return ResponseEntity.ok(result);
    }
}