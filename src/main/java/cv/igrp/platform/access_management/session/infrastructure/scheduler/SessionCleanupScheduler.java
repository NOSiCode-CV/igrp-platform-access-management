package cv.igrp.platform.access_management.session.infrastructure.scheduler;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.config.SessionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class SessionCleanupScheduler {

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;

    // Configuration properties
    private final long cleanupIntervalSeconds;
    private final long oldSessionRetentionDays;

    public SessionCleanupScheduler(
            SessionRepository sessionRepository,
            SessionCacheEvictService sessionCacheEvictService,
            @Value("${igrp.session.cleanup.interval-seconds:300}") long cleanupIntervalSeconds,
            @Value("${igrp.session.old-session.retention-days:30}") long oldSessionRetentionDays) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.oldSessionRetentionDays = oldSessionRetentionDays;
    }

    /**
     * Scheduled cleanup of expired sessions
     * Runs every configured interval (default: 5 minutes)
     */
    @Scheduled(fixedDelayString = "${igrp.session.cleanup.interval-seconds:300}000")
    public void cleanupExpiredSessions() {
        log.debug("Starting scheduled cleanup of expired sessions");

        try {
            Instant now = Instant.now();
            List<SessionEntity> expiredSessions = sessionRepository
                    .findExpiredSessions(SessionStatus.ACTIVE, now);

            if (expiredSessions.isEmpty()) {
                log.debug("No expired sessions found for cleanup");
                return;
            }

            // Mark sessions as expired
            expiredSessions.forEach(session -> {
                session.expire();
                log.debug("Marking session {} as expired for user: {}", 
                        session.getSessionId(), session.getUserExternalId());
            });

            // Save to database
            sessionRepository.saveAll(expiredSessions);

            // Collect user IDs for cache eviction
            Set<String> userIds = expiredSessions.stream()
                    .map(SessionEntity::getUserExternalId)
                    .collect(java.util.stream.Collectors.toSet());

            // Evict from cache
            sessionCacheEvictService.evictBySubjects(userIds);

            log.info("Cleaned up {} expired sessions and evicted {} cache entries", 
                    expiredSessions.size(), userIds.size());

        } catch (Exception e) {
            log.error("Error during expired session cleanup", e);
        }
    }

    /**
     * Scheduled cleanup of old closed/expired/revoked sessions
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "${igrp.session.old-session.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldSessions() {
        log.info("Starting scheduled cleanup of old sessions");

        try {
            Instant cutoffDate = Instant.now().minusSeconds(oldSessionRetentionDays * 24L * 60L * 60L);
            
            int deletedCount = sessionRepository.deleteOldSessions(
                    List.of(SessionStatus.CLOSED, SessionStatus.EXPIRED, SessionStatus.REVOKED),
                    cutoffDate);

            log.info("Deleted {} old session records older than {}", 
                    deletedCount, cutoffDate);

        } catch (Exception e) {
            log.error("Error during old session cleanup", e);
        }
    }

    /**
     * Health check for the cleanup scheduler
     */
    public CleanupHealth getHealth() {
        try {
            // Check if we can access the repository
            long activeCount = sessionRepository.countByStatus(SessionStatus.ACTIVE);
            
            // Check cache service
            var cacheStats = sessionCacheEvictService.getCacheStats();
            
            return new CleanupHealth(
                    true,
                    "Scheduler is healthy",
                    activeCount,
                    cacheStats.keyCount(),
                    cacheStats.available()
            );
        } catch (Exception e) {
            log.error("Health check failed", e);
            return new CleanupHealth(false, e.getMessage(), 0, 0, false);
        }
    }

    /**
     * Health status record
     */
    public record CleanupHealth(
            boolean healthy,
            String message,
            long activeSessionCount,
            long cacheKeyCount,
            boolean cacheAvailable
    ) {}
}
