package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Phase C2 — sliding heartbeat for accepted, session-bound requests.
 *
 * <p>The enforcement filter calls {@link #touch(SessionEntity, Instant, long)}
 * after a successful authorization check. To avoid hammering the DB on every
 * authenticated request, a Redis "debounce gate" is held for
 * {@code heartbeatDebounceSeconds}: while the gate is present we skip the DB
 * write.
 *
 * <p>Doubles as a small SID → snapshot cache (separate from the user-keyed
 * {@code sessionCache}) so the filter can resolve a session in a single Redis
 * round-trip on the hot path.
 */
@Service
public class SessionHeartbeatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionHeartbeatService.class);
    private static final String GATE_KEY_PREFIX = "igrp:session:heartbeat:";
    private static final String SID_KEY_PREFIX = "igrp:session:bySid:";
    private static final Duration SID_CACHE_TTL = Duration.ofSeconds(60);

    private final SessionRepository sessionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public SessionHeartbeatService(SessionRepository sessionRepository,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.sessionRepository = sessionRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Lightweight projection cached in Redis. Carries everything the
     * enforcement filter needs to make a decision without round-tripping JPA.
     */
    public record Snapshot(UUID sessionId,
                           String userId,
                           SessionStatus status,
                           Instant lastSeenAt,
                           Instant expiresAt,
                           Instant absoluteExpiresAt) {

        public static Snapshot from(SessionEntity entity) {
            return new Snapshot(
                    entity.getSessionId(),
                    entity.getUserId(),
                    entity.getStatus(),
                    entity.getLastSeenAt(),
                    entity.getExpiresAt(),
                    entity.getAbsoluteExpiresAt());
        }
    }

    /**
     * Update {@code last_seen_at} only when the persisted value is older than
     * the configured debounce window AND no Redis gate is held. Best-effort —
     * failures must not fail the originating request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(SessionEntity session, Instant now, long debounceSeconds) {
        if (session == null || session.getSessionId() == null) {
            return;
        }
        long debounce = Math.max(1L, debounceSeconds);
        Instant lastSeen = session.getLastSeenAt();
        if (lastSeen != null && lastSeen.plusSeconds(debounce).isAfter(now)) {
            return;
        }
        if (!claimDebounceGate(session.getSessionId(), debounce)) {
            return;
        }
        try {
            session.setLastSeenAt(now);
            SessionEntity saved = sessionRepository.save(session);
            cache(saved);
        } catch (DataAccessException ex) {
            LOGGER.warn("Heartbeat: failed to persist last_seen_at for sid={}: {}",
                    session.getSessionId(), ex.getMessage());
        }
    }

    /**
     * Look up the SID hot-path snapshot in Redis. Returns empty when Redis is
     * unavailable or there is no cached entry.
     */
    public Optional<Snapshot> findCached(UUID sid) {
        if (sid == null) {
            return Optional.empty();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(SID_KEY_PREFIX + sid);
            if (cached instanceof Snapshot snapshot) {
                return Optional.of(snapshot);
            }
        } catch (Exception ex) {
            LOGGER.debug("Heartbeat: SID cache read failed for {}: {}", sid, ex.getMessage());
        }
        return Optional.empty();
    }

    /** Populate the Redis SID hot-path cache. Best-effort; silent on failures. */
    public void cache(SessionEntity session) {
        if (session == null || session.getSessionId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    SID_KEY_PREFIX + session.getSessionId(),
                    Snapshot.from(session),
                    SID_CACHE_TTL.toSeconds(),
                    TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.debug("Heartbeat: SID cache write failed for {}: {}",
                    session.getSessionId(), ex.getMessage());
        }
    }

    /**
     * Drop the SID cache entry — used when a session transitions out of ACTIVE
     * so the enforcement filter sees the new state on the next request.
     */
    public void evict(UUID sid) {
        if (sid == null) {
            return;
        }
        try {
            redisTemplate.delete(SID_KEY_PREFIX + sid);
        } catch (Exception ex) {
            LOGGER.debug("Heartbeat: SID cache evict failed for {}: {}", sid, ex.getMessage());
        }
    }

    private boolean claimDebounceGate(UUID sid, long debounceSeconds) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    GATE_KEY_PREFIX + sid,
                    Long.toString(System.currentTimeMillis()),
                    Duration.ofSeconds(debounceSeconds));
            // null = Redis unavailable; fall through to write-through (the
            // lastSeenAt comparison above already provides DB-side debounce).
            return acquired == null || acquired;
        } catch (Exception ex) {
            LOGGER.debug("Heartbeat: gate acquisition failed for {}: {}", sid, ex.getMessage());
            return true;
        }
    }
}
