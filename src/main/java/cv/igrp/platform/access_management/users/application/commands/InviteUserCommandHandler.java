package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;
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
    private final RoleEntityRepository roleRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final InvitationEntityRepository invitationRepository;
    private final InvitationMapper invitationMapper;
    private final UserUtils userUtils;
    private final IGRPUserEntityRepository userRepository;

    public InviteUserCommandHandler(NotificationAdapter<NotificationResult> notificationAdapter,
                                    RoleEntityRepository roleRepository,
                                    DepartmentEntityRepository departmentRepository,
                                    InvitationEntityRepository invitationRepository,
                                    InvitationMapper invitationMapper,
                                    UserUtils userUtils,
                                    IGRPUserEntityRepository userRepository
    ) {
        this.notificationAdapter = notificationAdapter;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.invitationRepository = invitationRepository;
        this.invitationMapper = invitationMapper;
        this.userUtils = userUtils;
        this.userRepository = userRepository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<InvitationDTO> handle(InviteUserCommand command) {

        var dto = command.getInviteuserdto();

        if(dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_EMAIL_REQUIRED);
        }

        String normalizedValue = dto.getEmail().trim().toLowerCase();
        dto.setEmail(normalizedValue);

        // Block re-invites for accounts that already exist as ACTIVE or
        // INACTIVE. The case-insensitive lookup runs against the same
        // lowercased value stored on t_user.email, so John.Doe@Email.com
        // resolves the same row as john.doe@email.com. DELETED accounts are
        // intentionally permitted to be re-invited (the soft-delete is the
        // signal that a new identity for that email can be onboarded);
        // TEMPORARY accounts are also permitted (it's a resend semantically
        // — their first acceptance flow may have stalled).
        userRepository.findByEmailIgnoreCase(normalizedValue).ifPresent(existing -> {
            if (existing.getStatus() == Status.ACTIVE || existing.getStatus() == Status.INACTIVE) {
                LOGGER.warn("Rejecting invitation for {} — user already exists with status={}",
                        normalizedValue, existing.getStatus());
                throw IgrpResponseStatusException.of(
                        IgrpErrorCode.IGRP_AUTH_INVITATION_USER_ALREADY_EXISTS,
                        normalizedValue,
                        existing.getStatus().name());
            }
        });

        LOGGER.info("Inviting new user: type=EMAIL, value={}", dto.getEmail());

        // Cancel any previous pending invitations
        var previousInvitationOpt = invitationRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                IdentifierType.EMAIL.name(), dto.getEmail(), InvitationStatus.PENDING);

        if (previousInvitationOpt.isPresent()) {
            var previousInvitation = previousInvitationOpt.get();
            previousInvitation.setStatus(InvitationStatus.CANCELED);
            invitationRepository.save(previousInvitation);
            LOGGER.info("Previous invitation {} was cancelled for user {}.", previousInvitation.getId(),
                    dto.getEmail());
        }

        // Create new invitation
        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType(IdentifierType.EMAIL.name());
        invitation.setIdentifierValue(dto.getEmail());

        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setToken(UUID.randomUUID().toString());

        Set<RoleEntity> roles = new HashSet<>();
        var department = departmentRepository
                .findByCodeAndStatusNotDeleted(command.getInviteuserdto().getDepartmentCode());

        for (var roleCode : command.getInviteuserdto().getRoles()) {
            var role = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleCode);
            roles.add(role);
        }

        invitation.setRoles(roles);
        var savedInvitation = invitationRepository.saveAndFlush(invitation);

        var url = userUtils.constructInvitationUrl(appCenterUrl, savedInvitation.getToken());

        try {
            LOGGER.info("Inviting new user via email: token={}, email={}", savedInvitation.getToken(), dto.getEmail());

                var notification = new Notification();
                notification.setRecipients(List.of(dto.getEmail()));
                notification.setSubject("iGRP User Invitation");
                notification.setContent(
                        emailTemplate.replace("{{user}}", dto.getEmail()).replace("{{url}}", url));
                notification.setMetadata(
                        Map.of("invitationToken", savedInvitation.getToken(), "email", dto.getEmail()));

                notificationAdapter.send(notification);
        } catch (Exception e) {
            LOGGER.error("Invitation Email failed", e);
        }

        LOGGER.info("User invited successfully with token={}", savedInvitation.getToken());
        return ResponseEntity.ok(invitationMapper.toDtoWithUrl(savedInvitation, url));

    }

}
