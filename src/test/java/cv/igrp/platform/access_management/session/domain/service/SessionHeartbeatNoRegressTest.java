package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A heartbeat must extend the idle deadline, never shorten it. A token issuance
 * can push expires_at past now + idle-timeout so the session outlives the access
 * token; a later API call must not pull that deadline back in.
 */
class SessionHeartbeatNoRegressTest {

    private SessionRepository repository;
    private SessionHeartbeatService service;
    private final Instant now = Instant.parse("2026-09-10T10:00:00Z");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(SessionRepository.class);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(repository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SessionHeartbeatService(repository, redis);
    }

    private SessionEntity session(Instant expiresAt) {
        SessionEntity s = new SessionEntity();
        s.setSessionId(UUID.randomUUID());
        s.setLastSeenAt(now.minusSeconds(120)); // older than the debounce window
        s.setExpiresAt(expiresAt);
        s.setAbsoluteExpiresAt(now.plus(Duration.ofHours(8)));
        return s;
    }

    @Test
    void heartbeat_doesNotShortenDeadlineSetByRefresh() {
        Instant setByRefresh = now.plusSeconds(3900); // access-token TTL + grace
        SessionEntity s = session(setByRefresh);

        service.touch(s, now, 30, 1800L);

        assertEquals(setByRefresh, s.getExpiresAt());
        assertEquals(now, s.getLastSeenAt());
    }

    @Test
    void heartbeat_extendsDeadlineWhenLater() {
        SessionEntity s = session(now.plusSeconds(100));

        service.touch(s, now, 30, 1800L);

        assertEquals(now.plusSeconds(1800), s.getExpiresAt());
    }

    @Test
    void heartbeat_neverExceedsAbsoluteCeiling() {
        SessionEntity s = session(now.plusSeconds(10));
        s.setAbsoluteExpiresAt(now.plusSeconds(600));

        service.touch(s, now, 30, 1800L);

        assertEquals(now.plusSeconds(600), s.getExpiresAt());
    }
}
