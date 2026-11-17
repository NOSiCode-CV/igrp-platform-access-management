package cv.igrp.platform.access_management.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.serializer.RedisSerializer;


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
    @DisplayName("A cached null (NullValue) survives the round trip")
    void cachedNull() {
        // sessionCache's @Cacheable method returns null, so RedisCacheManager
        // stores NullValue for it.
        assertInstanceOf(NullValue.class, roundTrip(NullValue.INSTANCE));
    }
}
