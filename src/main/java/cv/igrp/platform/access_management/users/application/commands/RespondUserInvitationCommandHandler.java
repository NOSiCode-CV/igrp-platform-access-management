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
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserIdentifierEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class RespondUserInvitationCommandHandler
      implements CommandHandler<RespondUserInvitationCommand, ResponseEntity<InvitationDTO>> {

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
   private final UserIdentifierEntityRepository userIdentifierEntityRepository;
   private final UserIdentityResolutionService userIdentityResolutionService;
   private final OtpEntityRepository otpEntityRepository;

   public RespondUserInvitationCommandHandler(
         NotificationAdapter<NotificationResult> notificationAdapter,
         IGRPUserEntityRepository userRepository,
         RoleEntityRepository roleRepository,
         InvitationEntityRepository invitationRepository,
         InvitationMapper invitationMapper,
         SecurityAuditService auditService,
         UserIdentifierEntityRepository userIdentifierEntityRepository,
         UserIdentityResolutionService userIdentityResolutionService,
         OtpEntityRepository otpEntityRepository) {
      this.notificationAdapter = notificationAdapter;
      this.userRepository = userRepository;
      this.roleRepository = roleRepository;
      this.invitationRepository = invitationRepository;
      this.invitationMapper = invitationMapper;
      this.auditService = auditService;
      this.userIdentifierEntityRepository = userIdentifierEntityRepository;
      this.userIdentityResolutionService = userIdentityResolutionService;
      this.otpEntityRepository = otpEntityRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<InvitationDTO> handle(RespondUserInvitationCommand command) {

      var dto = command.getUserinvitationresponsedto();

      LOGGER.info("Responding to user invitation: token={}", command.getToken());

      // Find invitation
      var invitation = invitationRepository.findByTokenAndStatusPending(command.getToken());

      // Get authenticated user's OIDC Context
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      cv.igrp.platform.access_management.shared.security.UserProfile profile;

      if (authentication
            .getPrincipal() instanceof cv.igrp.platform.access_management.shared.security.IgrpOidcUser oidcUser) {
         profile = oidcUser.getUserProfile();
      } else {
         throw IgrpResponseStatusException.of(
               HttpStatus.UNAUTHORIZED,
               "Native OIDC User Context required to accept invitation");
      }

      String authMethod = profile.authMethod() != null ? profile.authMethod() : "pwd";
      // String nic = profile.externalId();
      // String nic = profile.nic();
      String externalId = profile.externalId();
      String nic = (profile.nic() != null && !profile.nic().isBlank()) ? profile.nic() : null;
      String phone = profile.phone();
      String email = profile.email();

      if (dto.isAccept()) {

         var otpEntityOpt = otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(command.getToken(), "APPROVED");
         
         if (otpEntityOpt.isPresent()) {
             invitation.setOtpId(otpEntityOpt.get().getId());
         } else {
             throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST,
                    "O código OTP não foi validado. Por favor, valide o seu código OTP antes de aceitar o convite.");
         }

         invitation.setStatus(InvitationStatus.ACCEPTED);
         var updatedInvitation = invitationRepository.save(invitation);

         // Resolve or create user via identity resolution service
         // boolean userExisted = userRepository.findByAnyIdentifier(email != null ?
         // email.toLowerCase() : null, nic, nic, phone).isPresent();
         boolean userExisted = userRepository
               .findByAnyIdentifier(email != null ? email.toLowerCase() : null, externalId, nic, phone).isPresent();

         // IGRPUserEntity user = userIdentityResolutionService.resolveOrCreate(nic,
         // email, nic, phone, profile.fullName());
         IGRPUserEntity user = userIdentityResolutionService.resolveOrCreate(externalId, email, nic, phone,
               profile.fullName());
         boolean isNewUser = !userExisted;

         if (invitation.getRoles() != null && !invitation.getRoles().isEmpty()) {
            Integer roleId = invitation.getRoles().iterator().next().getId();
            user.setActiveRole(roleRepository.findById(roleId).orElse(null));
         }

         var savedUser = userRepository.save(user);
         auditService.logUserChange(savedUser.getId(), isNewUser ? "CREATE" : "UPDATE");

         for (var role : invitation.getRoles()) {
            var roleEntity = roleRepository.findById(role.getId()).orElseThrow(() -> IgrpResponseStatusException.of(
                  HttpStatus.NOT_FOUND,
                  "Role with ID <%s> was not found".formatted(role.getId())));

            if (roleEntity.getUsers() == null) {
               roleEntity.setUsers(new HashSet<>());
            }
            roleEntity.getUsers().add(savedUser);
            roleRepository.save(roleEntity);
         }

         // Upsert secondary identifiers
         if (email != null) {
            var emailIdOpt = userIdentifierEntityRepository.findByTypeAndValueNormalized("EMAIL", email.toLowerCase());
            if (emailIdOpt.isEmpty()) {
               cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity emailEntity = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity();
               emailEntity.setUser(savedUser);
               emailEntity.setType("EMAIL");
               emailEntity.setValueNormalized(email.toLowerCase());
               emailEntity.setVerified(true);
               userIdentifierEntityRepository.save(emailEntity);
            }
         }

         if (phone != null) {
            var phoneIdOpt = userIdentifierEntityRepository.findByTypeAndValueNormalized("PHONE", phone);
            if (phoneIdOpt.isEmpty()) {
               cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity phoneEntity = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity();
               phoneEntity.setUser(savedUser);
               phoneEntity.setType("PHONE");
               phoneEntity.setValueNormalized(phone);
               phoneEntity.setVerified(true);
               userIdentifierEntityRepository.save(phoneEntity);
            }
         }

         try {
            if (savedUser.getEmail() != null && !savedUser.getEmail().isBlank()) {
               LOGGER.info("Notifying user: id={}, email={}", savedUser.getId(), savedUser.getEmail());

               var notification = new Notification();
               notification.setRecipients(List.of(savedUser.getEmail()));
               notification.setSubject("iGRP Invitation Response");
               notification.setContent(emailTemplate.replace("{{user}}", savedUser.getEmail()));
               notification.setMetadata(Map.of("userId", savedUser.getId(), "email", savedUser.getEmail()));

               notificationAdapter.send(notification);
               LOGGER.info("User with id={} was notified.", savedUser.getId());
            }
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