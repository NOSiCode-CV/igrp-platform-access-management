package cv.igrp.platform.access_management.oauth_server.infrastructure.bootstrap;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds the default iGRP OAuth client on first startup when it does not
 * already exist. The seeded secret comes from configuration and is BCrypt-
 * encoded before being persisted.
 */
@Configuration
public class DefaultOAuthClientBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOAuthClientBootstrap.class);
    public static final String DEFAULT_CLIENT_ID = "igrp-access-management";

    private final OAuthClientJpaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultClientId;
    private final String defaultClientSecret;
    private final int accessTokenTtl;
    private final int refreshTokenTtl;
    private final int authorizationCodeTtl;
    private final String redirectUri;

    public DefaultOAuthClientBootstrap(
            OAuthClientJpaRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${igrp.oauth.default-client.client-id:" + DEFAULT_CLIENT_ID + "}") String defaultClientId,
            @Value("${igrp.oauth.default-client.secret:}") String defaultClientSecret,
            @Value("${igrp.oauth.default-client.access-token-ttl:3600}") int accessTokenTtl,
            @Value("${igrp.oauth.default-client.refresh-token-ttl:86400}") int refreshTokenTtl,
            @Value("${igrp.oauth.default-client.authorization-code-ttl:300}") int authorizationCodeTtl,
            @Value("${igrp.oauth.default-client.redirect-uri:}") String redirectUri) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.defaultClientId = defaultClientId;
        this.defaultClientSecret = defaultClientSecret;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.authorizationCodeTtl = authorizationCodeTtl;
        this.redirectUri = redirectUri;
    }

    @org.springframework.context.annotation.Bean
    public CommandLineRunner seedDefaultOAuthClient() {
        return args -> {
            if (defaultClientSecret == null || defaultClientSecret.isBlank()) {
                LOGGER.info("Skipping default OAuth client seeding: igrp.oauth.default-client.secret not set");
                return;
            }
            if (repository.existsByClientId(defaultClientId)) {
                LOGGER.debug("Default OAuth client '{}' already present", defaultClientId);
                return;
            }

            OAuthClientEntity client = new OAuthClientEntity();
            client.setId(UUID.randomUUID());
            client.setClientId(defaultClientId);
            client.setClientSecret(passwordEncoder.encode(defaultClientSecret));
            client.setClientName("iGRP Access Management");
            client.setDescription("Default seeded OAuth2 client");
            client.setActive(true);
            client.setAccessTokenTtl(accessTokenTtl);
            client.setRefreshTokenTtl(refreshTokenTtl);
            client.setAuthorizationCodeTtl(authorizationCodeTtl);
            client.setScopes(new HashSet<>(Set.of("openid", "profile", "email")));
            client.setRedirectUris(redirectUri != null && !redirectUri.isBlank()
                    ? new HashSet<>(Set.of(redirectUri))
                    : new HashSet<>());
            client.setGrantTypes(new HashSet<>(Set.of("authorization_code", "refresh_token", "client_credentials")));
            repository.save(client);
            LOGGER.info("Seeded default OAuth client '{}'", defaultClientId);
        };
    }
}
