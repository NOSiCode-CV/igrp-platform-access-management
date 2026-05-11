package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

/**
 * Bridges the {@link OAuthClientEntity} aggregate persisted by the platform
 * to Spring Authorization Server's {@link RegisteredClient} contract.
 *
 * <p>The repository is read-only for the framework — CRUD happens through
 * the management REST API ({@code /api/clients}).
 * Inactive clients are invisible to the authorization server.
 */
public class IgrpRegisteredClientRepository implements RegisteredClientRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(IgrpRegisteredClientRepository.class);

    private final OAuthClientJpaRepository repository;

    public IgrpRegisteredClientRepository(OAuthClientJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        // Read-only: clients are managed through OAuthClientController.
    }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return repository.findById(UUID.fromString(id))
                    .filter(OAuthClientEntity::isActive)
                    .map(this::toRegisteredClient)
                    .orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .filter(OAuthClientEntity::isActive)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(OAuthClientEntity client) {
        RegisteredClient.Builder builder = RegisteredClient
                .withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientName(client.getClientName() != null ? client.getClientName() : client.getClientId())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtl()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtl()))
                        .authorizationCodeTimeToLive(Duration.ofSeconds(client.getAuthorizationCodeTtl()))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(false)
                        .requireAuthorizationConsent(false)
                        .build());

        if (client.getScopes() != null) {
            client.getScopes().forEach(builder::scope);
        }
        if (client.getRedirectUris() != null) {
            client.getRedirectUris().forEach(builder::redirectUri);
        }
        if (client.getGrantTypes() != null) {
            client.getGrantTypes().forEach(grant -> builder.authorizationGrantType(resolveGrantType(grant)));
        }

        builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);

        LOGGER.debug("Resolved RegisteredClient {} with grants {} scopes {}",
                client.getClientId(), client.getGrantTypes(), client.getScopes());

        return builder.build();
    }

    static AuthorizationGrantType resolveGrantType(String grant) {
        if (grant == null) {
            return new AuthorizationGrantType("");
        }
        return switch (grant.toLowerCase()) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "device_code" -> AuthorizationGrantType.DEVICE_CODE;
            default -> new AuthorizationGrantType(grant);
        };
    }
}
