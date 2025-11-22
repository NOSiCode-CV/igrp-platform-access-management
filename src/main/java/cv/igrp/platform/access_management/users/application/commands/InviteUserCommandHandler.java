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
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class InviteUserCommandHandler implements CommandHandler<InviteUserCommand, ResponseEntity<IGRPUserDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteUserCommandHandler.class);

    @Value("${igrp.mail.invite.template}")
    private String emailTemplate = """
                        Dear {{user}}, your were invited to the iGRP platform
                        
                        Best Regards.
                        iGRP
                        """;

    private final NotificationAdapter<NotificationResult> notificationAdapter;
    private final IGRPUserEntityRepository userRepository;
    private final RoleEntityRepository roleRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final IGRPUserMapper userMapper;
    private final IAdapter adapter;
    private final UpdateUserStatusCommandHandler commandBus;

    public InviteUserCommandHandler(NotificationAdapter<NotificationResult> notificationAdapter,
                                    IGRPUserEntityRepository userRepository,
                                    RoleEntityRepository roleRepository,
                                    DepartmentEntityRepository departmentRepository,
                                    IGRPUserMapper userMapper,
                                    IAdapter adapter,
                                    UpdateUserStatusCommandHandler commandBus
    ) {
        this.notificationAdapter = notificationAdapter;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.userMapper = userMapper;
        this.adapter = adapter;
        this.commandBus = commandBus;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<IGRPUserDTO> handle(InviteUserCommand command) {

        var dto = command.getInviteuserdto();

        LOGGER.info("Creating new user: email={}", dto.getEmail());

        // Verify if user exists
        if (userRepository.existsByEmail(dto.getEmail()))
            throw IgrpResponseStatusException.of(
                    HttpStatus.CONFLICT,
                    "User with email %s was invited already".formatted(dto.getEmail())
            );

        var providerUser = adapter.resolveUser(dto.getEmail());

        if (providerUser.isPresent()) {

            IGRPUserEntity user = new IGRPUserEntity();
            user.setEmail(command.getInviteuserdto().getEmail());
            user.setExternalId(providerUser.get().getExternalId());

            var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getInviteuserdto().getDepartmentCode());

            for(var roleName : command.getInviteuserdto().getRoles()) {
                var role = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleName);
                if(role.getUsers()==null) {
                    role.setUsers(new HashSet<>());
                }
                role.getUsers().add(user);
                roleRepository.save(role);
            }

            var savedUser = userRepository.save(user);

            final var disableUserCmd = new UpdateUserStatusCommand(Status.INACTIVE.getCode(), Integer.parseInt(savedUser.getId()));

            commandBus.handle(disableUserCmd);

            try {

                LOGGER.info("Inviting new user: id={}, email={}", savedUser.getId(), dto.getEmail());

                var notification = new Notification();

                notification.setRecipients(List.of(savedUser.getEmail()));
                notification.setSubject("iGRP User Invitation");
                notification.setContent(emailTemplate.replace("{{user}}", user.getEmail()));
                notification.setMetadata(Map.of("userId", savedUser.getId(), "email", savedUser.getEmail()));

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