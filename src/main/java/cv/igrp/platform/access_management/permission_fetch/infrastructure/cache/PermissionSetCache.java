package cv.igrp.platform.access_management.permission_fetch.infrastructure.cache;

import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.config.PermissionSetProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
     * KEYS: set, scopes hash, index, roles set, departments set, and optionally
     * the accessed guard. ARGV: ttl seconds, marker, number of roles, number of
     * departments, the roles, the departments, then key/scope pairs. Returns 1
     * when the guard is given and this is the first access in the TTL.
     */
    private static final RedisScript<Long> WRITE = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[4], KEYS[5])
            redis.call('SADD', KEYS[1], ARGV[2])
            local i = 5
            for _ = 1, tonumber(ARGV[3]) do
              redis.call('SADD', KEYS[4], ARGV[i])
              i = i + 1
            end
            for _ = 1, tonumber(ARGV[4]) do
              redis.call('SADD', KEYS[5], ARGV[i])
              i = i + 1
            end
            while i <= #ARGV do
              redis.call('SADD', KEYS[1], ARGV[i])
              redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
              i = i + 2
            end
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            for _, key in ipairs({KEYS[2], KEYS[4], KEYS[5]}) do
              if redis.call('EXISTS', key) == 1 then
                redis.call('EXPIRE', key, ARGV[1])
              end
            end
            redis.call('SADD', KEYS[3], KEYS[1])
            redis.call('EXPIRE', KEYS[3], ARGV[1])
            if #KEYS >= 6 and redis.call('SET', KEYS[6], '1', 'NX', 'EX', ARGV[1]) then
              return 1
            end
            return 0
            """, Long.class);

    /**
     * KEYS: the user's index. Deletes every set the index lists, with its
     * scopes hash and its roles and departments sets, then the index. The
     * accessed guards stay (see the class).
     */
    private static final RedisScript<Long> EVICT_USER = new DefaultRedisScript<>("""
            local sets = redis.call('SMEMBERS', KEYS[1])
            for _, set in ipairs(sets) do
              redis.call('DEL', set, set .. ':scopes', set .. ':roles', set .. ':departments')
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

    /** The caller's role codes, for the {@code by_role} scope (#39). Absent when there are none. */
    public static String rolesKey(String sub, String applicationCode) {
        return setKey(sub, applicationCode) + ":roles";
    }

    /** The caller's visible department codes, for the {@code department} scope (#39). Absent when there are none. */
    public static String departmentsKey(String sub, String applicationCode) {
        return setKey(sub, applicationCode) + ":departments";
    }

    /**
     * Replaces the caller's set for the application, with its scopes, roles
     * and departments.
     *
     * @return whether this is the caller's first access to the application in the TTL
     */
    public boolean write(String sub, String applicationCode, PermissionSetDTO set) {
        List<String> keys = new ArrayList<>(keys(sub, applicationCode));
        keys.add(accessedKey(sub, applicationCode));
        Long firstAccess = redis.execute(WRITE, keys, args(set));
        return firstAccess != null && firstAccess == 1L;
    }

    /**
     * Replaces a user's set for the application on an administrator's behalf
     * (#19). Not an access by the user, so the accessed guard is left alone.
     */
    public void writeForRefresh(String sub, String applicationCode, PermissionSetDTO set) {
        redis.execute(WRITE, keys(sub, applicationCode), args(set));
    }

    private static List<String> keys(String sub, String applicationCode) {
        return List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode), indexKey(sub),
                rolesKey(sub, applicationCode), departmentsKey(sub, applicationCode));
    }

    private Object[] args(PermissionSetDTO set) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(properties.getTtl().toSeconds()));
        args.add(MARKER);
        args.add(Integer.toString(set.roles().size()));
        args.add(Integer.toString(set.departments().size()));
        args.addAll(set.roles());
        args.addAll(set.departments());
        set.scopes().forEach((key, scope) -> {
            args.add(key);
            args.add(scope);
        });
        return args.toArray();
    }

    /** Deletes the caller's set for the application, so the next request reads the change. */
    public void evict(String sub, String applicationCode) {
        redis.delete(List.of(setKey(sub, applicationCode), scopesKey(sub, applicationCode),
                rolesKey(sub, applicationCode), departmentsKey(sub, applicationCode)));
        redis.opsForSet().remove(indexKey(sub), setKey(sub, applicationCode));
    }

    /** Deletes every set of the user, found through the per-user index, never a scan. */
    public void evictUser(String sub) {
        redis.execute(EVICT_USER, List.of(indexKey(sub)));
    }
}
