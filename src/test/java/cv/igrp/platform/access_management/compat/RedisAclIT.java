package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v3-09 (#17): only AM writes permission keys, enforced by Redis ACLs. The compat Redis runs
 * as deployments do: AM's own user ({@code igrp-am}), the services' read-only user
 * ({@code igrp-reader}), and the default user off. Every other compat test that writes a
 * permission set therefore already writes as {@code igrp-am}.
 */
class RedisAclIT extends AbstractCompatIT {

    @Autowired
    StringRedisTemplate redis;

    private LettuceConnectionFactory readerConnection;
    private StringRedisTemplate reader;

    @BeforeEach
    void connectAsReader() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                CompatEnvironment.REDIS.getHost(), CompatEnvironment.REDIS.getMappedPort(6379));
        server.setUsername(CompatEnvironment.REDIS_READER);
        server.setPassword(CompatEnvironment.REDIS_READER_PASSWORD);
        readerConnection = new LettuceConnectionFactory(server);
        readerConnection.afterPropertiesSet();
        readerConnection.start();
        reader = new StringRedisTemplate(readerConnection);
    }

    @AfterEach
    void disconnect() {
        readerConnection.destroy();
    }

    @Test
    @DisplayName("AM connects to Redis as its own user, igrp-am")
    void amIsItsOwnUser() {
        byte[] whoami = redis.execute(connection -> (byte[]) connection.execute("ACL",
                "WHOAMI".getBytes(StandardCharsets.UTF_8)), true);

        assertThat(new String(whoami, StandardCharsets.UTF_8)).isEqualTo(CompatEnvironment.REDIS_AM_USER);
    }

    @Test
    @DisplayName("A set AM writes is readable by the services' user, which cannot change or delete it")
    void readerReadsButCannotWrite() {
        String set = "permissions:user:" + UUID.randomUUID() + ":app:ACL_APP";
        redis.opsForSet().add(set, "~", "svc.faturas.list");

        assertThat(reader.opsForSet().members(set)).containsExactlyInAnyOrder("~", "svc.faturas.list");
        assertThatThrownBy(() -> reader.opsForSet().add(set, "svc.faturas.delete")).hasStackTraceContaining("NOPERM");
        assertThatThrownBy(() -> reader.delete(set)).hasStackTraceContaining("NOPERM");
        assertThat(redis.opsForSet().members(set)).containsExactlyInAnyOrder("~", "svc.faturas.list");
    }

    @Test
    @DisplayName("The services' user cannot read AM's other keys")
    void readerCannotReadOtherKeys() {
        String own = "am:own:" + UUID.randomUUID();
        redis.opsForValue().set(own, "x");

        assertThatThrownBy(() -> reader.opsForValue().get(own)).hasStackTraceContaining("NOPERM");
    }
}
