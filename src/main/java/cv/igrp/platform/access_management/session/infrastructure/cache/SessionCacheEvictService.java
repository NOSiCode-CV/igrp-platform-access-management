package cv.igrp.platform.access_management.session.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.RedisTemplate;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class SessionCacheEvictService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCacheEvictService.class);
    private static final String CACHE_PREFIX = "sessionCache::";
    private static final int SCAN_BATCH_SIZE = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionRepository sessionRepository;

    @Autowired
    public SessionCacheEvictService(RedisTemplate<String, Object> redisTemplate, SessionRepository sessionRepository) {
        this.redisTemplate = redisTemplate;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Evict all sessions from cache (emergency use only)
     */
    public void evictAll() {
        evictMatchingKeys(CACHE_PREFIX, key -> true);
        evictMatchingKeys(SessionHeartbeatService.SID_KEY_PREFIX, key -> true);
        LOGGER.warn("Evicted all sessions from cache - emergency operation");
    }

    /**
     * Evict session for a specific user
     */
    public void evictBySubject(String userId) {
        evictMatchingKeys(CACHE_PREFIX, key -> key.equals("%s%s".formatted(CACHE_PREFIX, userId)));
        evictSidsOf(Set.of(userId));
        LOGGER.info("Evicted session from cache for user: {}", userId);
    }

    /**
     * Evict sessions for multiple users
     */
    public void evictBySubjects(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }

        Set<String> keysToDelete = new HashSet<>();
        for (String userId : userIds) {
            keysToDelete.add("%s%s".formatted(CACHE_PREFIX, userId));
        }

        if (!keysToDelete.isEmpty()) {
            try {
                redisTemplate.delete(keysToDelete);
                LOGGER.info("Evicted sessions from cache for users: {}", userIds);
            } catch (Exception ex) {
                LOGGER.error("Redis unavailable while evicting sessions for users: {}", userIds, ex);
            }
        }
        evictSidsOf(userIds);
    }

    /**
     * Drop one session from the SID hot-path cache, once any surrounding
     * transaction has committed.
     */
    public void evictSid(UUID sid) {
        AfterCommit.run(() -> deleteQuietly(Set.of(SessionHeartbeatService.SID_KEY_PREFIX + sid)));
    }

    /**
     * The bulk revocations update rows with JPQL, which bypasses entity
     * listeners, so the users' sessions are looked up here.
     */
    private void evictSidsOf(Set<String> userIds) {
        AfterCommit.run(() -> {
            Set<String> keys = new HashSet<>();
            sessionRepository.findSessionIdsByUserIds(userIds)
                    .forEach(sid -> keys.add(SessionHeartbeatService.SID_KEY_PREFIX + sid));
            deleteQuietly(keys);
        });
    }

    private void deleteQuietly(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (Exception ex) {
            // The entry expires by itself within seconds.
            LOGGER.error("Redis unavailable while evicting {} SID entries", keys.size(), ex);
        }
    }

    /**
     * Evict sessions matching a specific predicate
     */
    private void evictMatchingKeys(String prefix, Predicate<String> predicate) {
        if (!isRedisAvailable()) {
            return;
        }

        Set<String> keysToDelete = new HashSet<>();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .keyCommands()
                .scan(KeyScanOptions.scanOptions().match("%s*".formatted(prefix)).count(SCAN_BATCH_SIZE).build())) {

            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                if (predicate.test(key)) {
                    keysToDelete.add(key);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Redis unavailable during scan operation", ex);
            return;
        }

        if (!keysToDelete.isEmpty()) {
            try {
                redisTemplate.delete(keysToDelete);
                LOGGER.info("Evicted session cache keys: {}", keysToDelete);
            } catch (Exception ex) {
                LOGGER.error("Redis unavailable while deleting session cache keys", ex);
            }
        }
    }

    /**
     * Check if Redis is available
     */
    private boolean isRedisAvailable() {
        try {
            var connection = redisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            return pong != null;
        } catch (Exception ex) {
            LOGGER.warn("Redis is not available: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    public CacheStats getCacheStats() {
        if (!isRedisAvailable()) {
            return new CacheStats(0, 0, false);
        }

        try {
            Set<String> keys = new HashSet<>();
            try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .keyCommands()
                    .scan(KeyScanOptions.scanOptions().match("%s*".formatted(CACHE_PREFIX)).build())) {

                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            }
            
            return new CacheStats(keys.size(), 0, true);
        } catch (Exception ex) {
            LOGGER.error("Error getting cache stats", ex);
            return new CacheStats(0, 0, false);
        }
    }

    /**
     * Cache statistics record
     */
    public record CacheStats(int keyCount, int hitRate, boolean available) {}
}
