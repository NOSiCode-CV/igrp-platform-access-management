package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class InviteUserCommandHandler implements CommandHandler<InviteUserCommand, ResponseEntity<InvitationDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteUserCommandHandler.class);

    @Value("${igrp.mail.invite.template}")
    private String emailTemplate = """
            Dear {{user}}, your were invited to the iGRP platform
            
            Please click on the link below to accept the invitation:
            {{url}}
            
            Best Regards.
            iGRP
            """;

    @Value("${igrp.app-center.url:}")
    private String appCenterUrl = "";

    private final NotificationAdapter<NotificationResult> notificationAdapter;
    private final IGRPUserEntityRepository userRepository;
    private final RoleEntityRepository roleRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final InvitationEntityRepository invitationRepository;
    private final InvitationMapper invitationMapper;
    private final UserUtils userUtils;
    private final IAdapter adapter;

    public InviteUserCommandHandler(NotificationAdapter<NotificationResult> notificationAdapter,
                                    IGRPUserEntityRepository userRepository,
                                    RoleEntityRepository roleRepository,
                                    DepartmentEntityRepository departmentRepository,
                                    InvitationEntityRepository invitationRepository,
                                    InvitationMapper invitationMapper,
                                    UserUtils userUtils,
                                    IAdapter adapter
    ) {
        this.notificationAdapter = notificationAdapter;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.invitationRepository = invitationRepository;
        this.invitationMapper = invitationMapper;
        this.userUtils = userUtils;
        this.adapter = adapter;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<InvitationDTO> handle(InviteUserCommand command) {

        var dto = command.getInviteuserdto();

        LOGGER.info("Inviting new user: email={}", dto.getEmail());

        // Verify if user exists
        if (userRepository.existsByEmail(dto.getEmail()))
            throw IgrpResponseStatusException.of(
                    HttpStatus.CONFLICT,
                    "User with email %s was invited already".formatted(dto.getEmail())
            );

        var providerUser = adapter.resolveUser(dto.getEmail());

        if (providerUser.isPresent()) {

            InvitationEntity invitation = new InvitationEntity();

            invitation.setEmail(dto.getEmail());
            invitation.setStatus(InvitationStatus.PENDING);
            invitation.setToken(UUID.randomUUID().toString());

            Set<RoleEntity> roles = new HashSet<>();

            var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getInviteuserdto().getDepartmentCode());

            for (var roleCode : command.getInviteuserdto().getRoles()) {
                var role = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleCode);
                roles.add(role);
            }

            invitation.setRoles(roles);

            var savedInvitation = invitationRepository.save(invitation);

            var url = userUtils.constructInvitationUrl(appCenterUrl, savedInvitation.getToken());

            try {

                LOGGER.info("Inviting new user: token={}, email={}", savedInvitation.getToken(), dto.getEmail());

                var notification = new Notification();

                notification.setRecipients(List.of(dto.getEmail()));
                notification.setSubject("iGRP User Invitation");
                notification.setContent(emailTemplate.replace("{{user}}", dto.getEmail()).replace("{{url}}", url));
                notification.setMetadata(Map.of("invitationToken", savedInvitation.getToken(), "email", dto.getEmail()));

                notificationAdapter.send(notification);

            } catch (Exception e) {
                LOGGER.error("Invitation Email failed", e);
            }

            LOGGER.info("User invited successfully with token={}", savedInvitation.getToken());

            return ResponseEntity.ok(invitationMapper.toDtoWithUrl(savedInvitation, url));

        } else {
            throw IgrpResponseStatusException.of(
                    HttpStatus.BAD_REQUEST,
                    "User Invitation Failed",
                    "The specified user does not exist in the Identity Provider."
            );
        }

    }

}