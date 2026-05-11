package cv.igrp.platform.access_management.session.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for session management
 */
@Getter
@Component
@ConfigurationProperties(prefix = "igrp.session")
public class SessionProperties {

    /**
     * Session timeout in seconds (default: 30 minutes)
     */
    private long timeoutSeconds = 1800L;

    /**
     * Absolute session lifetime ceiling in seconds (default: 8 hours).
     * Refresh-token rotations cannot push {@code expiresAt} past creation + this value.
     */
    private long absoluteTimeoutSeconds = 28800L;

    /**
     * Maximum concurrent ACTIVE sessions per user across distinct devices (default: 5).
     */
    private int maxPerUser = 5;

    /**
     * Minimum interval between {@code last_seen_at} writes for the enforcement
     * filter heartbeat (default: 30 seconds).
     */
    private long heartbeatDebounceSeconds = 30L;

    /**
     * Whether the {@code SessionEnforcementFilter} is wired into the OAuth2
     * resource-server chain (default: true). Flip to {@code false} as an
     * emergency switch — e.g. when Redis + DB are both down — so the chain
     * does not produce a 401 storm.
     */
    private boolean enforcementEnabled = true;

    /**
     * Session cleanup interval in seconds (default: 5 minutes)
     */
    private long cleanupIntervalSeconds = 300L;

    /**
     * Old session retention period in days (default: 30 days)
     */
    private long oldSessionRetentionDays = 30L;

    /**
     * Cron expression for old session cleanup (default: daily at 2 AM)
     */
    private String oldSessionCleanupCron = "0 0 2 * * ?";

    /**
     * Maximum session extension time in seconds (default: 2 hours)
     */
    private long maxExtensionSeconds = 7200L;

    /**
     * Minimum session extension time in seconds (default: 1 minute)
     */
    private long minExtensionSeconds = 60L;

    /**
     * Enable session cache (default: true)
     */
    private boolean cacheEnabled = true;

    /**
     * Session cache TTL in seconds (should match timeout)
     */
    private long cacheTtlSeconds = 1800L;

    /**
     * Enable session fixation protection (default: true)
     */
    private boolean fixationProtectionEnabled = true;

    /**
     * Enable IP tracking for sessions (default: true)
     */
    private boolean ipTrackingEnabled = true;

    /**
     * Enable user agent hashing for privacy (default: true)
     */
    private boolean userAgentHashingEnabled = true;
}
