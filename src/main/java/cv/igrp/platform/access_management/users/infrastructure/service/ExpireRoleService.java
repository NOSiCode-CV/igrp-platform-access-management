package cv.igrp.platform.access_management.users.infrastructure.service;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExpireRoleService {

    private static final Logger logger = LoggerFactory.getLogger(ExpireRoleService.class);

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TaskScheduler taskScheduler;
    private final SecurityAuditService securityAuditService;
    private final EventPublisher eventPublisher;

    public ExpireRoleService(UserRoleAssignmentRepository userRoleAssignmentRepository, 
                            TaskScheduler taskScheduler,
                            SecurityAuditService securityAuditService,
                            EventPublisher eventPublisher) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.taskScheduler = taskScheduler;
        this.securityAuditService = securityAuditService;
        this.eventPublisher = eventPublisher;
    }

    public void scheduleExpiration(UserRoleAssignment assignment) {
        if (assignment.getExpiresAt() != null) {
            Instant expirationInstant = assignment.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
            taskScheduler.schedule(() -> {
                logger.info("Executing scheduled expiration task for assignment User={} Role={}", 
                        assignment.getUser().getId(), assignment.getRole().getCode());
                removeExpiredRoles();
            }, expirationInstant);
        }
    }

    /**
     * Reap assignments whose {@code expires_at} has passed.
     *
     * <p>Note this is <em>housekeeping</em>, not enforcement. Authorization
     * already stops honouring an expired assignment the moment its timestamp
     * passes, because every permission query filters
     * {@code (ura.expires_at IS NULL OR ura.expires_at > NOW())} and
     * {@code IGRPUserEntity.getRoles()} filters client-side. Deleting the row
     * only keeps the table tidy.
     *
     * <p><b>Except through the cache.</b> Permission decisions are memoised in
     * Redis by {@code PermissionCacheService.getOrLoadPermissionCached}, and
     * that cache has no TTL. A decision cached as "granted" before expiry is
     * never re-derived, so the SQL filter above is correct but never consulted
     * again — the user keeps a privilege the expiry existed to remove. That is
     * why this method must publish {@link UserRoleChangedEvent}: it is the only
     * thing that reaches {@code PermissionCacheInvalidator} (which evicts the
     * user's cached decisions) and {@code SessionInvalidationEventListener}.
     * Every other path that changes role membership already publishes it; this
     * one did not.
     */
    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void removeExpiredRoles() {
        List<UserRoleAssignment> expiredRoles = userRoleAssignmentRepository.findExpiredRoles();
        if (expiredRoles.isEmpty()) {
            return;
        }

        logger.info("Found {} expired role assignments. Removing them.", expiredRoles.size());
        userRoleAssignmentRepository.deleteAll(expiredRoles);

        // One event per user rather than per assignment: eviction is keyed by
        // subject, so a user losing three roles at once needs one eviction, not
        // three identical ones.
        Map<String, Set<String>> expiredRoleCodesByUser = new LinkedHashMap<>();

        expiredRoles.forEach(assignment -> {
            String userId = assignment.getUser().getId();
            String roleCode = assignment.getRole().getCode();
            logger.debug("Expired role {} removed for user {}", roleCode, userId);

            java.util.Map<String, Object> auditContext = new java.util.HashMap<>();
            auditContext.put("userId", userId);
            auditContext.put("roleCode", roleCode);
            auditContext.put("reason", "EXPIRED");
            securityAuditService.logEvent(AuditEventType.ROLE_EXPIRED, AuditCategory.PRIVILEGE, auditContext);

            if (userId != null) {
                expiredRoleCodesByUser
                        .computeIfAbsent(userId, key -> new LinkedHashSet<>())
                        .add(roleCode);
            }
        });

        // Published inside the transaction, as every other role-change path
        // does. Safe here even though the delete has not committed: a concurrent
        // read that re-populates the cache re-runs the expires_at filter, so the
        // worst case is caching the correct "denied" answer.
        //
        // departmentCode is left null deliberately — no listener on this event
        // reads it, and resolving each role's department would mean a lazy load
        // per expired assignment for a field nothing consumes.
        expiredRoleCodesByUser.forEach((userId, roleCodes) ->
                eventPublisher.publishUserRoleChanged(new UserRoleChangedEvent(
                        userId,
                        roleCodes,
                        null,
                        UserRoleChangedEvent.CHANGE_EXPIRED,
                        "SYSTEM")));
    }
}
