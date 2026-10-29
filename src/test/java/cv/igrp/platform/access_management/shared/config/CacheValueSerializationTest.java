package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 migration gate — guards every value type this service writes
 * to Redis, through the serializer production uses ({@link CacheConfig#valueSerializer()}).
 *
 * <p>Values are read back as {@link Object}, exactly as {@code RedisCacheManager}
 * and {@code RedisTemplate<String, Object>} do, so the stored payload has to
 * carry its own type information. Written only against {@link RedisSerializer},
 * so the same test runs unchanged when the serializer moves from Jackson 2 to
 * Jackson 3.
 */
class CacheValueSerializationTest {

    private final RedisSerializer<Object> serializer = CacheConfig.valueSerializer();

    private Object roundTrip(Object value) {
        byte[] bytes = serializer.serialize(value);
        assertNotNull(bytes, "serialization produced no bytes");
        return serializer.deserialize(bytes);
    }

    @Test
    @DisplayName("permissionCache: a granted entry with roles survives the round trip")
    void permissionEntryWithRoles() {
        PermissionCacheEntryDTO entry = new PermissionCacheEntryDTO(
                true, new ArrayList<>(List.of("ADMIN", "MANAGER")), "Permission granted via role ADMIN");

        PermissionCacheEntryDTO back = assertInstanceOf(PermissionCacheEntryDTO.class, roundTrip(entry));

        assertEquals(entry, back);
    }

    @Test
    @DisplayName("permissionCache: a denied entry with no roles survives the round trip")
    void permissionEntryWithoutRoles() {
        // The compact constructor turns null into Collections.emptyList(), the
        // JDK sentinel class that has tripped typed deserialization before.
        PermissionCacheEntryDTO entry = new PermissionCacheEntryDTO(false, null, "Permission denied");

        PermissionCacheEntryDTO back = assertInstanceOf(PermissionCacheEntryDTO.class, roundTrip(entry));

        assertFalse(back.allowed());
        assertEquals(Collections.emptyList(), back.viaRoles());
        assertEquals("Permission denied", back.reason());
    }

    @Test
    @DisplayName("permissionCache: an entry built with List.of() survives the round trip")
    void permissionEntryWithImmutableList() {
        PermissionCacheEntryDTO entry = new PermissionCacheEntryDTO(true, List.of("ADMIN"), "granted");

        PermissionCacheEntryDTO back = assertInstanceOf(PermissionCacheEntryDTO.class, roundTrip(entry));

        assertEquals(List.of("ADMIN"), back.viaRoles());
    }

    @Test
    @DisplayName("A cached null (NullValue) survives the round trip")
    void cachedNull() {
        // sessionCache's @Cacheable method returns null, so RedisCacheManager
        // stores NullValue for it.
        assertInstanceOf(NullValue.class, roundTrip(NullValue.INSTANCE));
    }

    // ---- Pinned current behaviour: java.time values are NOT serializable ----
    //
    // GenericJackson2JsonRedisSerializer's default mapper registers no JSR-310
    // module, so any value holding an Instant fails to WRITE. Production never
    // sees it: SessionHeartbeatService swallows the failure at DEBUG, so the SID
    // hot-path cache is never populated and every lookup goes to Postgres.
    //
    // Jackson 3 handles java.time natively, so after the migration these writes
    // would start to succeed and the SID cache would come alive: a behaviour
    // change. The two tests below make that visible. They must be flipped
    // deliberately, together with the decision recorded in the migration plan,
    // never as a side effect.

    @Test
    @DisplayName("SID hot-path Snapshot is not serializable today (cache never populated)")
    void sidSnapshotIsNotSerializableToday() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        SessionHeartbeatService.Snapshot snapshot = new SessionHeartbeatService.Snapshot(
                UUID.randomUUID(), "user-1", SessionStatus.ACTIVE,
                now, now.plusSeconds(1800), now.plusSeconds(28800));

        assertThrows(SerializationException.class, () -> serializer.serialize(snapshot));
    }

    @Test
    @DisplayName("sessionCache: SessionResponseDTO is not serializable today (only NullValue is ever stored)")
    void sessionResponseIsNotSerializableToday() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        SessionResponseDTO session = new SessionResponseDTO();
        session.setSessionId(UUID.randomUUID());
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plusSeconds(1800));
        session.setClientIp("10.0.0.1");
        session.setDeviceId("device-1");
        session.setRoles(new ArrayList<>());
        session.setDepartments(new ArrayList<>());

        assertThrows(SerializationException.class, () -> serializer.serialize(session));
    }
}
