package cv.igrp.platform.access_management.shared.infrastructure.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class PermissionCacheEvictService {

    private static final String CACHE_PREFIX = "permissionCache::";
    private static final int SCAN_BATCH_SIZE = 1000;
    private static final int DELETE_BATCH_SIZE = 500;


    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public PermissionCacheEvictService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictAll() {
        evictMatchingKeys(key -> true);
    }

    public void evictBySubject(String subject) {
        evictMatchingKeys(key -> key.startsWith("%s%s:".formatted(CACHE_PREFIX, subject)));
    }

    public void evictByResource(String resource) {
        evictMatchingKeys(key -> key.matches("%s.*:%s:.*".formatted(CACHE_PREFIX, resource)));
    }

    public void evictByAction(String action) {
        evictMatchingKeys(key -> key.endsWith(":%s".formatted(action)));
    }

    public void evictByTriple(String subject, String resource, String action) {
        String key = "%s%s:%s:%s".formatted(CACHE_PREFIX, subject, resource, action);
        redisTemplate.delete(key);
    }

    private void evictMatchingKeys(Predicate<String> predicate) {
        Set<String> keysToDelete = new HashSet<>();

        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(KeyScanOptions.scanOptions().match("%s*".formatted(CACHE_PREFIX)).count(SCAN_BATCH_SIZE).build())) {

            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                if (predicate.test(key)) {
                    keysToDelete.add(key);
                }
            }
        }

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }
}
