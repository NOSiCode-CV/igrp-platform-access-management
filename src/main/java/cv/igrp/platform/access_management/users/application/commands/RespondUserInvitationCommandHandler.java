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
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService;
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
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
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
   private final UserRoleAssignmentRepository userRoleAssignmentRepository;
   private final ExpireRoleService expireRoleService;
   private final EventPublisher eventPublisher;

   public RespondUserInvitationCommandHandler(
         NotificationAdapter<NotificationResult> notificationAdapter,
         IGRPUserEntityRepository userRepository,
         RoleEntityRepository roleRepository,
         InvitationEntityRepository invitationRepository,
         InvitationMapper invitationMapper,
         SecurityAuditService auditService,
         UserIdentifierEntityRepository userIdentifierEntityRepository,
         UserIdentityResolutionService userIdentityResolutionService,
         OtpEntityRepository otpEntityRepository,
         UserRoleAssignmentRepository userRoleAssignmentRepository,
         ExpireRoleService expireRoleService,
         EventPublisher eventPublisher) {
      this.notificationAdapter = notificationAdapter;
      this.userRepository = userRepository;
      this.roleRepository = roleRepository;
      this.invitationRepository = invitationRepository;
      this.invitationMapper = invitationMapper;
      this.auditService = auditService;
      this.userIdentifierEntityRepository = userIdentifierEntityRepository;
      this.userIdentityResolutionService = userIdentityResolutionService;
      this.otpEntityRepository = otpEntityRepository;
      this.userRoleAssignmentRepository = userRoleAssignmentRepository;
      this.expireRoleService = expireRoleService;
      this.eventPublisher = eventPublisher;
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
      String idStr = profile.id();
      Integer userId;
      try {
          userId = Integer.parseInt(idStr);
      } catch (NumberFormatException e) {
          throw IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "Invalid Token sub: must be an integer ID");
      }
      String phone = profile.phone();
      String email = profile.email();

      String primaryIdentifierValue = null;
      if ("cmdcv".equalsIgnoreCase(authMethod)) {
         primaryIdentifierValue = phone;
      } else if ("cni".equalsIgnoreCase(authMethod)) {
         primaryIdentifierValue = profile.nic(); // Use the actual NIC from profile if it's CNI
      } else if ("pwd".equalsIgnoreCase(authMethod)) {
         primaryIdentifierValue = email != null ? email.toLowerCase() : null;
      }

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

         boolean isNewUser = false;
         IGRPUserEntity user = userRepository.findById(userId).orElse(null);
         if (user == null) {
             user = new IGRPUserEntity();
             isNewUser = true;
             // If we're creating a new user, we must ensure it gets the ID from the token if possible, 
             // but ID is auto-generated usually. If the IdP knows the ID, we might need to set it,
             // but JPA @GeneratedValue might ignore it. Let's just set the NIC.
             if (profile.nic() != null) {
                 user.setNic(profile.nic());
             } else {
                 user.setNic(idStr); // fallback for username/nic requirement
             }
         }
         
         if (email != null) {
             user.setEmail(email.toLowerCase());
         }
         
         if (profile.fullName() != null && !profile.fullName().isBlank()) {
             user.setName(profile.fullName());
         }

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

            UserRoleAssignment ura = new UserRoleAssignment(savedUser, roleEntity, null);
            ura.setAssignedAt(java.time.LocalDateTime.now());
            userRoleAssignmentRepository.save(ura);

            // Log role assignment
            java.util.Map<String, Object> auditContext = new java.util.HashMap<>();
            auditContext.put("userId", savedUser.getId());
            auditContext.put("roleCode", roleEntity.getCode());
            auditContext.put("source", "INVITATION");
            auditService.logEvent(cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType.ROLE_ASSIGNED,
                    cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory.PRIVILEGE, auditContext);
         }

         if (invitation.getRoles() != null && !invitation.getRoles().isEmpty()) {
            java.util.Set<String> grantedCodes = invitation.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(java.util.stream.Collectors.toSet());
            String departmentCode = invitation.getRoles().iterator().next().getDepartment() != null
                    ? invitation.getRoles().iterator().next().getDepartment().getCode()
                    : null;
            Integer savedUserId;
            try {
               savedUserId = Integer.parseInt(savedUser.getId());
            } catch (NumberFormatException nfe) {
               savedUserId = userId;
            }
            eventPublisher.publishUserRoleChanged(new UserRoleChangedEvent(
                    savedUserId, grantedCodes, departmentCode,
                    UserRoleChangedEvent.CHANGE_ADDED, "INVITATION"));
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