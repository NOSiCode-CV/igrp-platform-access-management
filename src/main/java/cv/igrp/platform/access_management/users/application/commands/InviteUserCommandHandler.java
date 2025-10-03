package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.mail.smtp.dto.SendNotificationResponseDTO;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class InviteUserCommandHandler implements CommandHandler<InviteUserCommand, ResponseEntity<IGRPUserDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteUserCommandHandler.class);

    @Value("${igrp.mail.invite.template}")
    private String emailTemplate = """
                        Dear {{user}}, your were successfully invited to iGRP. Your data are the following:
                        Username: {{user}}
                        
                        Best Regards.
                        iGRP
                        """;

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

        // Verify if username exists
        if (userRepository.existsByUsername(dto.getUsername()))
            throw IgrpResponseStatusException.of(
                    HttpStatus.CONFLICT,
                    "User with username %s was invited already".formatted(dto.getUsername())
            );

        var providerUser = adapter.resolveUser(dto.getUsername());

        if (providerUser.isPresent()) {

            IGRPUserEntity user = new IGRPUserEntity();
            user.setName(command.getIgrpuserdto().getName());
            user.setUsername(command.getIgrpuserdto().getUsername());
            user.setEmail(command.getIgrpuserdto().getEmail());
            user.setRoles(new ArrayList<>());

            var savedUser = userRepository.save(user);

            try {

                LOGGER.info("Inviting new user: username={}, email={}", dto.getUsername(), dto.getEmail());

                var notification = new Notification();

                notification.setRecipients(List.of(savedUser.getEmail()));
                notification.setSubject("iGRP User Invitation");
                notification.setContent(emailTemplate.replace("{{user}}", user.getUsername()));
                notification.setMetadata(Map.of("userId", savedUser.getId(), "username", savedUser.getUsername()));

                notificationAdapter.send(notification);

            } catch (Exception e) {
                LOGGER.error("Invitation Email failed", e);
            }

            LOGGER.info("User invited successfully with id={}", savedUser.getId());

            return ResponseEntity.ok(userMapper.toDto(savedUser));

        } else {
            throw IgrpResponseStatusException.of(
                    HttpStatus.BAD_REQUEST,
                    "User Invitation Failed",
                    "The specified user does not exist in the Identity Provider."
            );
        }

    }

}