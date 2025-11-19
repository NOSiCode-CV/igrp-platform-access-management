package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RespondUserInvitationCommandHandler implements CommandHandler<RespondUserInvitationCommand, ResponseEntity<IGRPUserDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RespondUserInvitationCommandHandler.class);

   @Value("${igrp.mail.invite.response.template}")
   private String emailTemplate = """
                        Dear {{user}}, you accepted the invite to the iGRP platform successfully.
                        
                        Best Regards.
                        iGRP
                        """;

   private final NotificationAdapter<NotificationResult> notificationAdapter;
   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final IAdapter adapter;
   private final CommandBus commandBus;

   public RespondUserInvitationCommandHandler(
           NotificationAdapter<NotificationResult> notificationAdapter,
           IGRPUserEntityRepository userRepository,
           IGRPUserMapper userMapper,
           IAdapter adapter,
           CommandBus commandBus
   ) {
       this.notificationAdapter = notificationAdapter;
       this.userRepository = userRepository;
       this.userMapper = userMapper;
       this.adapter = adapter;
       this.commandBus = commandBus;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<IGRPUserDTO> handle(RespondUserInvitationCommand command) {

      var dto = command.getUserinvitationresponsedto();

      LOGGER.info("Responding to user invitation: email={}", dto.getEmail());

      // Verify if user exists
      if (!userRepository.existsByEmail(dto.getEmail()))
         throw IgrpResponseStatusException.of(
                 HttpStatus.NOT_FOUND,
                 "User with email %s was not invited already".formatted(dto.getEmail())
         );

      var providerUserOpt = adapter.resolveUser(dto.getEmail());

      if (providerUserOpt.isPresent()) {

         var providerUser = providerUserOpt.get();

         var user = userRepository.findByExternalId(providerUser.getExternalId())
                 .orElseThrow(() -> IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "User with email %s was not invited already".formatted(dto.getEmail())
                 ));

         if(dto.isAccept()) {
            user.setStatus(Status.ACTIVE);
         } else {
            user.setEmail(UUID.randomUUID() + "_" + dto.getEmail());
            user.setStatus(Status.DELETED);
         }

         var savedUser = userRepository.save(user);

         if(dto.isAccept()) {

            final var enableUserCmd = new UpdateUserStatusCommand(Status.ACTIVE.getCode(), Integer.parseInt(savedUser.getId()));

            commandBus.send(enableUserCmd);

            try {

               LOGGER.info("Notifying new user: id={}, email={}", savedUser.getId(), dto.getEmail());

               var notification = new Notification();

               notification.setRecipients(List.of(savedUser.getEmail()));
               notification.setSubject("iGRP Invitation Response");
               notification.setContent(emailTemplate.replace("{{user}}", user.getEmail()));
               notification.setMetadata(Map.of("userId", savedUser.getId(), "email", savedUser.getEmail()));

               notificationAdapter.send(notification);

               LOGGER.info("User with id={} was notified.", savedUser.getId());

            } catch (Exception e) {
               LOGGER.error("Invitation Email failed", e);
            }
         }

         return ResponseEntity.ok(userMapper.toDto(savedUser));

      } else {

         LOGGER.error("Error while responding to user invitation: email={}", dto.getEmail());

         throw IgrpResponseStatusException.of(
                 HttpStatus.NOT_FOUND,
                 "User with email <%s> was not found in the Identity Provider".formatted(dto.getEmail())
         );

      }

   }

}