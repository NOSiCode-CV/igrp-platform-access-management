package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.RemoveRolesFromUserCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

   private final IGRPUserRepository userRepository;

   public RemoveRolesFromUserCommandHandler(IGRPUserRepository userRepository, RoleMapper roleMapper) {
      this.userRepository = userRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {
      // Buscar o usuário pelo ID
      IGRPUser user = userRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));

      List<Integer> roleIdsToRemove = command.getRoleIds();
      if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {
          user.getRoles().removeIf(role -> roleIdsToRemove.contains(role.getId()));
      }

      // Salvar o usuário após a remoção dos papéis
      userRepository.save(user);

      // Mapeando os papéis restantes do usuário para RoleDTO
      List<RoleDTO> result = user.getRoles().stream()
              .map(role -> new RoleDTO(role.getId(), role.getName(), role.getDescription(), null, null, null))  // Ajuste para converter para DTO
              .collect(Collectors.toList());

      // Retornar a lista atualizada de RoleDTO
      return ResponseEntity.ok(result);
   }
}
