package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class RespondUserInvitationCommandHandler implements CommandHandler<RespondUserInvitationCommand, ResponseEntity<InvitationDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RespondUserInvitationCommandHandler.class);

   @Value("${igrp.mail.invite.response.template}")
   private String emailTemplate = """
                        Dear {{user}}, you accepted the invite to the iGRP platform successfully.
                        
                        Best Regards.
                        iGRP
                        """;

   private final NotificationAdapter<NotificationResult> notificationAdapter;
   private final IGRPUserEntityRepository userRepository;
   private final RoleEntityRepository roleRepository;
   private final InvitationEntityRepository invitationRepository;
   private final InvitationMapper invitationMapper;
   private final SecurityAuditService auditService;

   public RespondUserInvitationCommandHandler(
           NotificationAdapter<NotificationResult> notificationAdapter,
           IGRPUserEntityRepository userRepository,
           RoleEntityRepository roleRepository,
           InvitationEntityRepository invitationRepository,
           InvitationMapper invitationMapper,
           SecurityAuditService auditService) {
       this.notificationAdapter = notificationAdapter;
       this.userRepository = userRepository;
       this.roleRepository = roleRepository;
       this.invitationRepository = invitationRepository;
       this.invitationMapper = invitationMapper;
       this.auditService = auditService;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<InvitationDTO> handle(RespondUserInvitationCommand command) {

      var dto = command.getUserinvitationresponsedto();

      LOGGER.info("Responding to user invitation: email={}, token={}", dto.getEmail(), command.getToken());

      // Find invitation
      var invitation = invitationRepository.findByTokenAndStatusPending(command.getToken());

      // Check if user already exists in DB
      if (userRepository.existsByEmail(dto.getEmail()))
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST,
                 "User with email %s already exists".formatted(dto.getEmail())
         );

      // Get authenticated user's JWT claims
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.UNAUTHORIZED,
                 "Authentication required to accept invitation"
         );
      }

      // Verify JWT email matches invitation email
      String jwtEmail = jwt.getClaimAsString("email");
      if (jwtEmail == null || !jwtEmail.equalsIgnoreCase(dto.getEmail())) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST,
                 "JWT email does not match invitation email"
         );
         
         
      }

      // Use JWT subject as external_id
      String externalId = jwt.getSubject();

      if(dto.isAccept()) {

         invitation.setStatus(InvitationStatus.ACCEPTED);
         var updatedInvitation = invitationRepository.save(invitation);

         IGRPUserEntity user = new IGRPUserEntity();
         user.setEmail(dto.getEmail());
         user.setExternalId(externalId);

         if(invitation.getRoles() != null &&  !invitation.getRoles().isEmpty()) {
            Integer roleId = invitation.getRoles().iterator().next().getId();
            user.setActiveRole(roleRepository.findById(roleId).orElse(null));
         }

         var savedUser = userRepository.save(user);
         auditService.logUserChange(savedUser.getId(), "CREATE");

         for(var role : invitation.getRoles()) {
            var roleEntity = roleRepository.findById(role.getId()).orElseThrow(() -> IgrpResponseStatusException.of(
                    HttpStatus.NOT_FOUND,
                    "Role with ID <%s> was not found".formatted(role.getId())
            ));

            if(roleEntity.getUsers() == null) {
               roleEntity.setUsers(new HashSet<>());
            }
            roleEntity.getUsers().add(savedUser);
            roleRepository.save(roleEntity);
         }

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

         return ResponseEntity.ok(invitationMapper.toDto(updatedInvitation));

      } else {
         invitation.setStatus(InvitationStatus.REJECTED);
         invitation.setComments(dto.getObservation());
         invitationRepository.save(invitation);
         return ResponseEntity.noContent().build();
      }
   }

}