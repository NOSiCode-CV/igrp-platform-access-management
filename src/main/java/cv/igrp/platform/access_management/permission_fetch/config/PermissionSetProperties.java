package cv.igrp.platform.access_management.permission_fetch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v3-06 (#14): the per-user permission sets AM writes to the shared Redis.
 * {@code igrp.permissions.cache.ttl} ({@code IGRP_PERMISSIONS_CACHE_TTL}),
 * 45 minutes by default: a missed invalidation heals itself within it.
 */
@ConfigurationProperties("igrp.permissions.cache")
public class PermissionSetProperties {

    private Duration ttl = Duration.ofMinutes(45);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("igrp.permissions.cache.ttl must be positive, was " + ttl);
        }
        this.ttl = ttl;
    }
}
