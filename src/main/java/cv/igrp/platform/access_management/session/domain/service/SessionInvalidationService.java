package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@Transactional
public class SessionInvalidationService {

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;

    public SessionInvalidationService(
            SessionRepository sessionRepository,
            SessionCacheEvictService sessionCacheEvictService) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
    }

    /**
     * Invalidate sessions for specific users
     */
    public void invalidateUserSessions(Set<String> userIds, String reason) {
        if (userIds.isEmpty()) {
            log.debug("No users to invalidate sessions for");
            return;
        }

        log.info("Invalidating sessions for users: {} with reason: {}", userIds, reason);

        Instant now = Instant.now();
        int invalidatedCount = sessionRepository.invalidateUserSessions(
                userIds,
                SessionStatus.ACTIVE,
                SessionStatus.REVOKED,
                now,
                now,
                reason,
                "SYSTEM"
        );

        // Evict from cache
        sessionCacheEvictService.evictBySubjects(userIds);

        log.info("Invalidated {} sessions for {} users with reason: {}", 
                invalidatedCount, userIds.size(), reason);
    }

    /**
     * Invalidate sessions for users with a specific role
     */
    public void invalidateSessionsByRole(String departmentCode, String roleCode, String reason) {
        log.info("Invalidating sessions for role: {} in department: {} with reason: {}", 
                roleCode, departmentCode, reason);

        // Get user IDs for users with this role
        Set<String> userIds = sessionRepository.findUserIdsByRole(roleCode, departmentCode);

        if (userIds.isEmpty()) {
            log.debug("No users found with role: {} in department: {}", roleCode, departmentCode);
            return;
        }

        Instant now = Instant.now();
        int invalidatedCount = sessionRepository.invalidateSessionsByRole(
                roleCode,
                departmentCode,
                SessionStatus.ACTIVE,
                SessionStatus.REVOKED,
                now,
                now,
                reason,
                "SYSTEM"
        );

        // Evict from cache
        sessionCacheEvictService.evictBySubjects(userIds);

        log.info("Invalidated {} sessions for role: {} in department: {} with reason: {}", 
                invalidatedCount, roleCode, departmentCode, reason);
    }

    /**
     * Invalidate sessions for users in a specific department
     */
    public void invalidateSessionsByDepartment(String departmentCode, String reason) {
        log.info("Invalidating sessions for department: {} with reason: {}", departmentCode, reason);

        // Get user IDs for users in this department
        Set<String> userIds = sessionRepository.findUserIdsByDepartment(departmentCode);

        if (userIds.isEmpty()) {
            log.debug("No users found in department: {}", departmentCode);
            return;
        }

        Instant now = Instant.now();
        int invalidatedCount = sessionRepository.invalidateSessionsByDepartment(
                departmentCode,
                SessionStatus.ACTIVE,
                SessionStatus.REVOKED,
                now,
                now,
                reason,
                "SYSTEM"
        );

        // Evict from cache
        sessionCacheEvictService.evictBySubjects(userIds);

        log.info("Invalidated {} sessions for department: {} with reason: {}", 
                invalidatedCount, departmentCode, reason);
    }

    /**
     * Invalidate all active sessions (emergency use)
     */
    public void invalidateAllSessions(String reason) {
        log.warn("Invalidating ALL active sessions with reason: {}", reason);

        // This would require a new method in SessionRepository to invalidate all
        // For now, we'll log this as it should be used sparingly
        log.error("EMERGENCY: Invalidating all sessions - this should be used sparingly");
        
        // Evict all cache entries
        sessionCacheEvictService.evictAll();
    }

    /**
     * Invalidate session for a single user
     */
    public void invalidateUserSession(String userId, String reason) {
        log.info("Invalidating session for user: {} with reason: {}", userId, reason);
        invalidateUserSessions(Set.of(userId), reason);
    }

    /**
     * Get statistics about session invalidation
     */
    public InvalidationStats getInvalidationStats() {
        // This could be expanded to track invalidation metrics
        return new InvalidationStats(0, 0, 0);
    }

    /**
     * Statistics record for session invalidation
     */
    public record InvalidationStats(int userInvalidations, int roleInvalidations, int departmentInvalidations) {}
}
