package cv.igrp.platform.access_management.compat;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared infrastructure for the Spring Boot 4 migration gate ({@code compat}
 * package): one Postgres and one Redis container for the whole test JVM, a
 * fixed HTTP port, and an empty database installed through the real path.
 *
 * <p><b>Fixed port.</b> This service is its own OAuth2 issuer. The resource
 * server resolves {@code AUTH_JWT_ISSUER} lazily through
 * {@code /.well-known/openid-configuration}, and the issuer has to be known when
 * the properties are bound, before a random port would exist.
 *
 * <p><b>Empty database.</b> Postgres starts empty, so every run installs the
 * service from scratch: {@code FreshDatabaseSchemaCallback} lays down the base
 * schema, Flyway applies every migration, and the production profile's
 * {@code ddl-auto=validate} checks the result against the entities.
 *
 * <p>Everything here is plain Testcontainers and property names, with no
 * Spring Boot type that moved in Boot 4, so the harness runs unchanged on both
 * lines.
 */
public final class CompatEnvironment {

    /** The superadmin bootstrapped by {@code ConfigurationService} at startup. */
    public static final String SUPERADMIN_ID = "0b6c1b8e-8d3e-4b0a-9a4f-000000000001";
    public static final String SUPERADMIN_EMAIL = "superadmin@igrp.cv";

    /** The suite runs the configuration that is deployed, not the bare default. */
    public static final String PROFILE = "production";

    public static final int PORT = freePort();
    public static final String ISSUER = "http://localhost:" + PORT;

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    /**
     * The external IdP users log in through, as Autentika/WSO2 are in
     * production. A real Keycloak, so the federated login flow runs end to end.
     */
    @SuppressWarnings("resource")
    static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                    .withExposedPorts(8080)
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("compat/keycloak-realm.json"),
                            "/opt/keycloak/data/import/compat-realm.json")
                    .withCommand("start-dev", "--import-realm")
                    .waitingFor(Wait.forHttp("/realms/compat/.well-known/openid-configuration")
                            .forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    public static final String KEYCLOAK_CLIENT_ID = "igrp-access-management";
    public static final String KEYCLOAK_CLIENT_SECRET = "compat-keycloak-secret";
    public static final String KEYCLOAK_USER = "fulano";
    public static final String KEYCLOAK_PASSWORD = "compat-password";
    public static final String KEYCLOAK_EMAIL = "fulano@example.cv";

    static {
        POSTGRES.start();
        REDIS.start();
        KEYCLOAK.start();
    }

    public static final String KEYCLOAK_ADAPTER_CLIENT_ID = "igrp-am-adapter";

    public static String keycloakServerUrl() {
        return "http://localhost:" + KEYCLOAK.getMappedPort(8080);
    }

    public static String keycloakIssuer() {
        return keycloakServerUrl() + "/realms/compat";
    }

    private CompatEnvironment() {
    }

    static void register(DynamicPropertyRegistry registry) {
        properties().forEach((name, value) -> registry.add(name, () -> value));
    }

    private static Map<String, Object> properties() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("server.port", PORT);
        p.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        p.put("spring.datasource.username", POSTGRES.getUsername());
        p.put("spring.datasource.password", POSTGRES.getPassword());
        p.put("spring.data.redis.host", REDIS.getHost());
        p.put("spring.data.redis.port", REDIS.getMappedPort(6379));

        // Everything else (JPA ddl-auto=validate, lazy loading, actuator
        // exposure, discovery) comes from the production profile itself, which
        // the suite runs under: see AbstractCompatIT.

        // This service validates the tokens it issues itself.
        p.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", ISSUER);
        p.put("igrp.oauth.issuer", ISSUER);
        p.put("igrp.access.api.base-url", ISSUER);

        p.put("igrp.superadmin.user-external-id", SUPERADMIN_ID);
        p.put("igrp.superadmin.email", SUPERADMIN_EMAIL);
        // RouteVersionGateIT runs the deletion job itself; the scheduled run would race it.
        p.put("igrp.routes.deletion-check-interval", "365d");
        p.put("igrp.app-center.url", "http://localhost:3000");

        // Federated login through the Keycloak container.
        p.put("igrp.oauth.external-idp.enabled", "true");
        p.put("igrp.oauth.external-idp.clock-check-enabled", "false");
        p.put("spring.security.oauth2.client.registration.external-idp.client-id", KEYCLOAK_CLIENT_ID);
        p.put("spring.security.oauth2.client.registration.external-idp.client-secret", KEYCLOAK_CLIENT_SECRET);
        p.put("spring.security.oauth2.client.provider.external-idp.issuer-uri", keycloakIssuer());

        // Federated service clients (v3-17) live in the same realm, managed by AM's adapter account.
        p.put("igrp.keycloak.server-url", keycloakServerUrl());
        p.put("igrp.keycloak.realm", "compat");
        p.put("igrp.keycloak.client-id", KEYCLOAK_ADAPTER_CLIENT_ID);
        p.put("igrp.keycloak.client-secret", "compat-adapter-secret");

        // Storage is in memory (CompatStorageConfiguration); these only let the
        // configured provider's beans start.
        p.put("igrp.minio.endpoint", "localhost");
        p.put("igrp.minio.port", "1");
        p.put("igrp.minio.security", "false");
        p.put("igrp.minio.access-key", "compat");
        p.put("igrp.minio.secret-key", "compat-secret");
        p.put("igrp.s3.aws-endpoint", "http://localhost:1");
        p.put("igrp.s3.aws-access-key", "compat");
        p.put("igrp.s3.aws-secret-key", "compat-secret");
        p.put("igrp.s3.secure", "false");

        p.put("otel.exporter.otlp.endpoint", "http://localhost:1");
        p.put("otel.sdk.disabled", "true");
        p.put("igrp.audit.chain.secret", "compat-audit-chain-secret-0123456789abcdef");
        return p;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
