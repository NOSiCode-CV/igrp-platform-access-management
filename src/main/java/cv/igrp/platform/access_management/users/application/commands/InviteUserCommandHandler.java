package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.mail.smtp.dto.SendNotificationResponseDTO;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class InviteUserCommandHandler implements CommandHandler<InviteUserCommand, ResponseEntity<IGRPUserDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(InviteUserCommandHandler.class);

   private final NotificationAdapter<SendNotificationResponseDTO> notificationAdapter;
   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final IAdapter adapter;

   public InviteUserCommandHandler(NotificationAdapter<SendNotificationResponseDTO> notificationAdapter,
                                   IGRPUserEntityRepository userRepository,
                                   IGRPUserMapper userMapper,
                                   IAdapter adapter
   ) {
      this.notificationAdapter = notificationAdapter;
      this.userRepository = userRepository;
      this.userMapper = userMapper;
      this.adapter = adapter;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<IGRPUserDTO> handle(InviteUserCommand command) {

      var dto = command.getIgrpuserdto();

      LOGGER.info("Creating new user: username={}, email={}", dto.getUsername(), dto.getEmail());

      IGRPUserEntity user = new IGRPUserEntity();
      user.setName(command.getIgrpuserdto().getName());
      user.setUsername(command.getIgrpuserdto().getUsername());
      user.setEmail(command.getIgrpuserdto().getEmail());
      user.setRoles(new ArrayList<>());

      var savedUser = userRepository.save(user);

      try {
         adapter.createUser(user);
      } catch (IAMException e) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "User Creation Failed",
                 e.getMessage()
         );
      }

      try {

         LOGGER.info("Inviting new user: username={}, email={}", dto.getUsername(), dto.getEmail());

         var notification = new Notification();

         notification.setRecipients(List.of(savedUser.getEmail()));
         notification.setSubject("iGRP User Invitation");
         notification.setContent("""
                 Dear %s, your account has been created for iGRP. Your credentials are the following:
                 
                 Username: %s
                 Password: %s
                 
                 Best Regards.
                 iGRP
                 
                 """.formatted(Objects.nonNull(savedUser.getName()) ? savedUser.getName() : savedUser.getUsername(), savedUser.getUsername(), savedUser.getUsername()));
         notification.setMetadata(Map.of("userId", savedUser.getId()));

         notificationAdapter.send(notification);

      } catch (Exception e) {
         LOGGER.error("Invitation Email failed", e);
      }

      LOGGER.info("User created successfully with id={}", savedUser.getId());

      return ResponseEntity.ok(userMapper.toDto(savedUser));

   }

}