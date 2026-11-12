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
 *       plus the {@link #MARKER} member, present in every set, so a user with
 *       no permissions is still a hit (Redis cannot store an empty Set).
 *       Readers check a key with SISMEMBER and treat "the key exists" as a hit;
 *       the marker is never a permission key.</li>
 *   <li>{@code permissions:user:{sub}:app:{appCode}:scopes}: a Hash, key to
 *       scope. Absent when the set has no keys.</li>
 *   <li>{@code permissions:user:{sub}:apps}: the per-user index of the user's
 *       set keys, so eviction never scans Redis.</li>
 *   <li>{@code permissions:user:{sub}:app:{appCode}:accessed}: AM's own
 *       once-per-TTL guard for {@code APPLICATION_ACCESSED}; eviction leaves it,
 *       so a refill after a change is not a second access. Not for readers.</li>
 * </ul>
 * One Lua script writes them all with their TTL, so a crash never leaves a key
 * without an expiry. The script touches several keys, so it needs a single
 * Redis node (standalone or Sentinel), not Redis Cluster.
 */
@Component
public class PermissionSetCache {

    /** Present in every permission set; never a permission key (keys use letters, digits, '.', '-', '_'). */
    public static final String MARKER = "~";

    /**
     * KEYS: set, scopes hash, index, and optionally the accessed guard. ARGV:
     * ttl seconds, marker, then key/scope pairs. Returns 1 when the guard is
     * given and this is the first access in the TTL.
     */
    private static final RedisScript<Long> WRITE = new DefaultRedisScript<>("""
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
            if #KEYS >= 4 and redis.call('SET', KEYS[4], '1', 'NX', 'EX', ARGV[1]) then
              return 1
            end
            return 0
            """, Long.class);

    /**
     * KEYS: the user's index. Deletes every set the index lists, with its
     * scopes hash, then the index. The accessed guards stay (see the class).
     */
    private static final RedisScript<Long> EVICT_USER = new DefaultRedisScript<>("""
            local sets = redis.call('SMEMBERS', KEYS[1])
            for _, set in ipairs(sets) do
              redis.call('DEL', set, set .. ':scopes')
            end
            redis.call('DEL', KEYS[1])
            return #sets
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

    static String accessedKey(String sub, String applicationCode) {
        return setKey(sub, applicationCode) + ":accessed";
    }

    /**
     * Replaces the caller's set for the application.
     *
     * @return whether this is the caller's first access to the application in the TTL
     */
    public boolean write(String sub, String applicationCode, Map<String, String> scopesByKey) {
        Long firstAccess = redis.execute(WRITE,
                List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode), indexKey(sub),
                        accessedKey(sub, applicationCode)),
                args(scopesByKey));
        return firstAccess != null && firstAccess == 1L;
    }

    /**
     * Replaces a user's set for the application on an administrator's behalf
     * (#19). Not an access by the user, so the accessed guard is left alone.
     */
    public void writeForRefresh(String sub, String applicationCode, Map<String, String> scopesByKey) {
        redis.execute(WRITE,
                List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode), indexKey(sub)),
                args(scopesByKey));
    }

    private Object[] args(Map<String, String> scopesByKey) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(properties.getTtl().toSeconds()));
        args.add(MARKER);
        scopesByKey.forEach((key, scope) -> {
            args.add(key);
            args.add(scope);
        });
        return args.toArray();
    }

    /** Deletes the caller's set for the application, so the next request reads the change. */
    public void evict(String sub, String applicationCode) {
        redis.delete(List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode)));
        redis.opsForSet().remove(indexKey(sub), setKey(sub, applicationCode));
    }

    /** Deletes every set of the user, found through the per-user index, never a scan. */
    public void evictUser(String sub) {
        redis.execute(EVICT_USER, List.of(indexKey(sub)));
    }
}
