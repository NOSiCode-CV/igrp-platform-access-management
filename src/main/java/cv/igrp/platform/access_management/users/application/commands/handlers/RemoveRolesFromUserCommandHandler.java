package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.UserRepository;
import cv.igrp.platform.access_management.shared.domain.models.User;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.RemoveRolesFromUserCommand;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

   private final UserRepository userRepository;
   //private final RoleMapper roleMapper;

   public RemoveRolesFromUserCommandHandler(UserRepository userRepository/*, RoleMapper roleMapper*/) {
      this.userRepository = userRepository;
      //this.roleMapper = roleMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {
      /*User user = userRepository.findById(command.getUserId())
              .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getUserId()));

      user.getRoles().removeIf(role -> command.getRoleIds().contains(role.getId()));
      userRepository.save(user);

      List<RoleDTO> result = user.getRoles().stream().map(roleMapper::toDto).collect(Collectors.toList());*/
      return ResponseEntity.ok(new ArrayList<>());
   }
}