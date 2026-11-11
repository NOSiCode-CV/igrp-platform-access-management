package cv.igrp.platform.access_management.permission_fetch.infrastructure.cache;

import cv.igrp.platform.access_management.permission_fetch.config.PermissionSetProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The shared-Redis contract with the 0.3 starter (v3-06, spec "Cache"). Only
 * AM writes it.
 * <ul>
 *   <li>{@code permissions:user:{sub}:app:{appCode}}: a Set of permission keys
 *       plus the {@link #MARKER} member, so a user with no permissions is still
 *       a hit (Redis cannot store an empty Set). Readers ignore the marker.</li>
 *   <li>{@code permissions:user:{sub}:app:{appCode}:scopes}: a Hash, key to
 *       scope. Absent when the set has no keys.</li>
 *   <li>{@code permissions:user:{sub}:apps}: the per-user index of the user's
 *       set keys, so eviction never scans Redis.</li>
 * </ul>
 * One Lua script writes all three with their TTL, so a crash never leaves a
 * key without an expiry.
 */
@Component
public class PermissionSetCache {

    /** Present in every permission set; never a permission key (keys use letters, digits, '.', '-', '_'). */
    public static final String MARKER = "~";

    /**
     * KEYS: set, scopes hash, index. ARGV: ttl seconds, marker, then key/scope pairs.
     * Returns 1 when the set existed before this write.
     */
    private static final RedisScript<Long> WRITE = new DefaultRedisScript<>("""
            local existed = redis.call('EXISTS', KEYS[1])
            redis.call('DEL', KEYS[1], KEYS[2])
            redis.call('SADD', KEYS[1], ARGV[2])
            for i = 3, #ARGV, 2 do
              redis.call('SADD', KEYS[1], ARGV[i])
              redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('EXPIRE', KEYS[2], ARGV[1])
            end
            redis.call('SADD', KEYS[3], KEYS[1])
            redis.call('EXPIRE', KEYS[3], ARGV[1])
            return existed
            """, Long.class);

    private final StringRedisTemplate redis;
    private final PermissionSetProperties properties;

    public PermissionSetCache(StringRedisTemplate redis, PermissionSetProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public static String setKey(String sub, String applicationCode) {
        return "permissions:user:" + sub + ":app:" + applicationCode;
    }

    public static String scopesKey(String sub, String applicationCode) {
        return setKey(sub, applicationCode) + ":scopes";
    }

    public static String indexKey(String sub) {
        return "permissions:user:" + sub + ":apps";
    }

    /**
     * Replaces the caller's set for the application.
     *
     * @return whether a set was already cached (a refresh rather than a first access in this TTL)
     */
    public boolean write(String sub, String applicationCode, Map<String, String> scopesByKey) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(properties.getTtl().toSeconds()));
        args.add(MARKER);
        scopesByKey.forEach((key, scope) -> {
            args.add(key);
            args.add(scope);
        });
        Long existed = redis.execute(WRITE,
                List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode), indexKey(sub)),
                args.toArray());
        return existed != null && existed == 1L;
    }

    /** Deletes the caller's set for the application, so the next request reads the change. */
    public void evict(String sub, String applicationCode) {
        redis.delete(List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode)));
        redis.opsForSet().remove(indexKey(sub), setKey(sub, applicationCode));
    }
}
