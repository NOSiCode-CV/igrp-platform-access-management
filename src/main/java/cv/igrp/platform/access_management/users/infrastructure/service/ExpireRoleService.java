package cv.igrp.platform.access_management.users.infrastructure.service;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class ExpireRoleService {

    private static final Logger logger = LoggerFactory.getLogger(ExpireRoleService.class);

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TaskScheduler taskScheduler;
    private final SecurityAuditService securityAuditService;

    public ExpireRoleService(UserRoleAssignmentRepository userRoleAssignmentRepository, 
                            TaskScheduler taskScheduler,
                            SecurityAuditService securityAuditService) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.taskScheduler = taskScheduler;
        this.securityAuditService = securityAuditService;
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

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void removeExpiredRoles() {
        List<UserRoleAssignment> expiredRoles = userRoleAssignmentRepository.findExpiredRoles();
        if (!expiredRoles.isEmpty()) {
            logger.info("Found {} expired role assignments. Removing them.", expiredRoles.size());
            userRoleAssignmentRepository.deleteAll(expiredRoles);
            
            // Further audit logging points could be fired here
            expiredRoles.forEach(assignment -> {
                logger.debug("Expired role {} removed for user {}", assignment.getRole().getCode(), assignment.getUser().getId());
                
                java.util.Map<String, Object> auditContext = new java.util.HashMap<>();
                auditContext.put("userId", assignment.getUser().getId());
                auditContext.put("roleCode", assignment.getRole().getCode());
                auditContext.put("reason", "EXPIRED");
                securityAuditService.logEvent(AuditEventType.ROLE_EXPIRED, AuditCategory.PRIVILEGE, auditContext);
            });
        }
    }
}
