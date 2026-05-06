package cv.igrp.platform.access_management.shared.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class PermissionCacheEvictService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCacheEvictService.class);

    private static final String CACHE_PREFIX = "permissionCache::";
    private static final int SCAN_BATCH_SIZE = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PermissionCacheEvictService(RedisTemplate<String, Object> redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void evictBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return;
        }
        evictMatchingKeys(key -> key.startsWith("%s%s:".formatted(CACHE_PREFIX, subject)));
    }

    public void evictByUserId(Integer userId) {
        if (userId == null) {
            return;
        }

        List<String> subjects = jdbcTemplate.query(
                "SELECT external_id FROM t_user WHERE id = ? AND status <> 'DELETED'",
                (rs, rowNum) -> rs.getString("external_id"),
                userId
        );
        evictBySubjects(subjects);
    }

    public void evictByRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }

        List<String> subjects = jdbcTemplate.query(
                """
                SELECT DISTINCT u.external_id
                FROM t_user u
                JOIN t_user_role_assignment ura ON ura.user_id = u.id
                JOIN t_role r ON r.id = ura.role_id
                WHERE r.code = ?
                  AND u.status <> 'DELETED'
                  AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                """,
                (rs, rowNum) -> rs.getString("external_id"),
                roleCode
        );
        evictBySubjects(subjects);
    }

    public void evictByDepartment(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return;
        }

        List<String> subjects = jdbcTemplate.query(
                """
                SELECT DISTINCT u.external_id
                FROM t_user u
                JOIN t_user_role_assignment ura ON ura.user_id = u.id
                JOIN t_role r ON r.id = ura.role_id
                JOIN t_department d ON d.id = r.department
                WHERE d.code = ?
                  AND u.status <> 'DELETED'
                  AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                """,
                (rs, rowNum) -> rs.getString("external_id"),
                departmentCode
        );
        evictBySubjects(subjects);
    }

    public void evictByResource(String resource) {
        if (resource == null || resource.isBlank()) {
            return;
        }
        evictMatchingKeys(key -> key.matches("%s.*:%s:.*".formatted(CACHE_PREFIX, resource)));
    }

    public void evictByAction(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        evictMatchingKeys(key -> key.endsWith(":%s".formatted(action)));
    }

    public void evictBySubjects(Collection<String> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return;
        }

        subjects.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(subject -> !subject.isBlank())
                .forEach(this::evictBySubject);
    }

    public void evictByTriple(String subject, String resource, String action) {
        if (!isRedisAvailable()) return;
        String key = "%s%s:%s:%s".formatted(CACHE_PREFIX, subject, resource, action);
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            LOGGER.error("Redis unavailable while evicting triple [{}:{}:{}]", subject, resource, action, ex);
        }
    }

    private void evictMatchingKeys(Predicate<String> predicate) {
        if (!isRedisAvailable()) return;

        Set<String> keysToDelete = new HashSet<>();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(KeyScanOptions.scanOptions().match("%s*".formatted(CACHE_PREFIX)).count(SCAN_BATCH_SIZE).build())) {

            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
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
                LOGGER.info("The following cache keys were deleted: {}", keysToDelete);
            } catch (Exception ex) {
                LOGGER.error("Redis unavailable while deleting keys", ex);
            }
        }
    }

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
}
