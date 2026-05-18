package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleId;
import cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
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
   private final SessionAuditLogger sessionAuditLogger;

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
         EventPublisher eventPublisher,
         SessionAuditLogger sessionAuditLogger) {
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
      this.sessionAuditLogger = sessionAuditLogger;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<InvitationDTO> handle(RespondUserInvitationCommand command) {

      var dto = command.getUserinvitationresponsedto();

      LOGGER.info("Responding to user invitation: token={}", command.getToken());

      // Find invitation — explicit status check so a repeat-acceptance returns
      // a clean 409 instead of a misleading 404 from the PENDING-only finder.
      var invitation = invitationRepository.findByTokenOrThrow(command.getToken());
      if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
         throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_ALREADY_ACCEPTED);
      }
      if (invitation.getStatus() == InvitationStatus.REJECTED) {
         throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_ALREADY_REJECTED);
      }
      if (invitation.getStatus() != InvitationStatus.PENDING) {
         throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_NOT_PENDING, invitation.getStatus());
      }

      // Get authenticated user's OIDC Context
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      cv.igrp.platform.access_management.shared.security.UserProfile profile;

      if (authentication
            .getPrincipal() instanceof cv.igrp.platform.access_management.shared.security.IgrpOidcUser oidcUser) {
         profile = oidcUser.getUserProfile();
      } else {
         throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_RESPONSE_UNAUTHORIZED);
      }

      String authMethod = profile.authMethod() != null ? profile.authMethod() : "pwd";
      String idStr = profile.id();
      // Phase G1 / FR-13: SubjectParser raises InvalidPrincipalException (→ 401)
      // instead of NumberFormatException when sub is non-numeric (M2M shape).
      String userId = cv.igrp.platform.access_management.shared.security.SubjectParser
              .parseUserSubjectOrThrow(idStr);
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
             throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_OTP_NOT_VALIDATED);
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

         // Phase G3: invitation acceptance promotes TEMPORARY → ACTIVE.
         // For pre-existing accounts the status must be TEMPORARY; otherwise
         // we skip the transition (account already activated or in a terminal
         // state) but the audit row records the no-op.
         Status oldStatusForAudit;
         boolean statusChangedToActive = false;
         if (isNewUser) {
            oldStatusForAudit = null;
            user.setStatus(Status.ACTIVE);
            statusChangedToActive = true;
         } else if (user.getStatus() == Status.TEMPORARY) {
            oldStatusForAudit = Status.TEMPORARY;
            user.setStatus(Status.ACTIVE);
            statusChangedToActive = true;
         } else {
            oldStatusForAudit = user.getStatus();
            LOGGER.warn("Invitation accept on user id={} with non-TEMPORARY status={} — leaving status unchanged",
                  user.getId(), user.getStatus());
         }

         var savedUser = userRepository.save(user);
         auditService.logUserChange(savedUser.getId(), isNewUser ? "CREATE" : "UPDATE");

         String auditUserId = savedUser.getId() != null ? savedUser.getId() : userId;
         if (statusChangedToActive) {
            String oldCode = oldStatusForAudit == null ? null : oldStatusForAudit.getCode();
            eventPublisher.publishUserStatusChanged(new UserStatusChangedEvent(
                  auditUserId, oldCode, Status.ACTIVE.getCode(), "INVITATION_ACCEPTED"));
            try {
               sessionAuditLogger.recordUserStatusTransitioned(
                     auditUserId, oldCode, Status.ACTIVE.getCode(),
                     SessionAuditLogger.USER, "INVITATION_ACCEPTED");
            } catch (Exception ex) {
               LOGGER.warn("[G3 audit] recordUserStatusTransitioned failed (accept): {}", ex.getMessage());
            }
         }

         for (var role : invitation.getRoles()) {
            var roleEntity = roleRepository.findById(role.getId()).orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ROLE_NOT_FOUND_BY_ID, role.getId()));

            // Idempotency without NonUniqueObjectException: search the user's
            // already-managed userRoleAssignments collection first. Using
            // userRoleAssignmentRepository.findById(uraId) would issue a SEPARATE
            // SELECT and return a fresh managed instance — distinct from whatever
            // instance the user.userRoleAssignments collection already holds in
            // the Hibernate session (e.g. lazy-loaded by an earlier touch or by
            // cascade-merge during userRepository.save(user)). At flush time
            // Hibernate would then see two managed entities with the same
            // composite PK UserRoleId(userId, roleId) and throw
            // NonUniqueObjectException. Mirror the pattern already used by
            // AddRolesToUserCommandHandler: mutate-in-place when present,
            // otherwise add a fresh entity to the collection and let
            // @OneToMany(cascade=ALL) on IGRPUserEntity.userRoleAssignments
            // persist it on flush.
            java.util.Optional<UserRoleAssignment> existingUra = savedUser.getUserRoleAssignments().stream()
                    .filter(assignment -> assignment.getRole() != null
                            && assignment.getRole().getId() != null
                            && assignment.getRole().getId().equals(roleEntity.getId()))
                    .findFirst();
            if (existingUra.isPresent()) {
               UserRoleAssignment ura = existingUra.get();
               ura.setExpiresAt(null);
               ura.setAssignedAt(java.time.LocalDateTime.now());
            } else {
               // The UserRoleAssignment(user, role, ...) constructor is
               // bytecode-enhanced by Hibernate: setting `this.user = user`
               // triggers bidirectional management and auto-appends the new
               // URA to `user.userRoleAssignments`. Calling .add() here would
               // duplicate the entry in the collection and produce the
               // NonUniqueObjectException at flush time. Construct only.
               UserRoleAssignment ura = new UserRoleAssignment(savedUser, roleEntity, null);
               ura.setAssignedAt(java.time.LocalDateTime.now());
            }

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
            String savedUserId = savedUser.getId() != null ? savedUser.getId() : userId;
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

         // Phase G3: invitation rejection terminally flips TEMPORARY → DELETED
         // for the authenticated principal. Missing-user / non-TEMPORARY cases
         // are logged and skipped — the rejection itself still stands.
         IGRPUserEntity rejectedUser = userRepository.findById(userId).orElse(null);
         if (rejectedUser != null) {
            Status currentStatus = rejectedUser.getStatus();
            if (currentStatus == Status.TEMPORARY) {
               rejectedUser.setStatus(Status.DELETED);
               userRepository.save(rejectedUser);
               eventPublisher.publishUserStatusChanged(new UserStatusChangedEvent(
                     userId, Status.TEMPORARY.getCode(), Status.DELETED.getCode(),
                     "INVITATION_REJECTED"));
               try {
                  sessionAuditLogger.recordUserStatusTransitioned(
                        userId, Status.TEMPORARY.getCode(), Status.DELETED.getCode(),
                        SessionAuditLogger.USER, "INVITATION_REJECTED");
               } catch (Exception ex) {
                  LOGGER.warn("[G3 audit] recordUserStatusTransitioned failed (reject): {}", ex.getMessage());
               }
            } else {
               LOGGER.warn("Invitation reject on user id={} with non-TEMPORARY status={} — leaving status unchanged",
                     rejectedUser.getId(), currentStatus);
            }
         }
         return ResponseEntity.noContent().build();
      }
   }

}