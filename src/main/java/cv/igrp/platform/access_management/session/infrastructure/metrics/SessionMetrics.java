package cv.igrp.platform.access_management.session.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Phase F2 — Micrometer counters for the session lifecycle.
 *
 * <p>Names follow the {@code igrp_session_*_total} convention so they survive
 * the dot-to-underscore mapping in Prometheus exposition unchanged. Tag-bearing
 * counters use the {@code reason} key so dashboards can split, e.g.,
 * {@code session_revoked} vs {@code session_expired} rejections.
 */
@Component
public class SessionMetrics {

    public static final String CREATED = "igrp.session.created";
    public static final String EVICTED_LRU = "igrp.session.evicted.lru";
    public static final String REJECTED_REVOKED = "igrp.session.rejected.revoked";
    public static final String REFRESH_REJECTED = "igrp.session.refresh.rejected";
    public static final String HEARTBEAT = "igrp.session.heartbeat";

    private final MeterRegistry registry;
    private final Counter created;
    private final Counter evictedLru;
    private final Counter heartbeat;

    public SessionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.created = Counter.builder(CREATED)
                .description("Server-side sessions opened by the authorization server")
                .register(registry);
        this.evictedLru = Counter.builder(EVICTED_LRU)
                .description("Sessions closed because the per-user LRU cap was hit")
                .register(registry);
        this.heartbeat = Counter.builder(HEARTBEAT)
                .description("Authenticated requests that touched last_seen_at via the enforcement filter")
                .register(registry);
    }

    public void recordCreated() {
        created.increment();
    }

    public void recordEvictedLru() {
        evictedLru.increment();
    }

    public void recordHeartbeat() {
        heartbeat.increment();
    }

    /**
     * Bumped from {@code SessionEnforcementFilter} every time a JWT is denied
     * because its bound session is no longer ACTIVE / has expired / was wiped
     * by a tokens-not-valid-before floor.
     */
    public void recordRejectedRevoked(String reason) {
        Counter.builder(REJECTED_REVOKED)
                .description("JWTs rejected because the bound session was revoked or expired")
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
    }

    /**
     * Bumped on the refresh-grant path when {@link
     * cv.igrp.platform.access_management.oauth_server.infrastructure.security.SessionIssuanceService}
     * rejects a refresh because the prior session is closed or hit its absolute
     * lifetime ceiling.
     */
    public void recordRefreshRejected(String reason) {
        Counter.builder(REFRESH_REJECTED)
                .description("Refresh-token grants rejected by the session issuance service")
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
    }
}
