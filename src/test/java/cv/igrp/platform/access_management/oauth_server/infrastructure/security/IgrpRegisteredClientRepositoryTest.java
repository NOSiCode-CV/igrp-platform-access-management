package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IgrpRegisteredClientRepositoryTest {

    @Mock
    private OAuthClientJpaRepository jpaRepository;

    private IgrpRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        repository = new IgrpRegisteredClientRepository(jpaRepository);
    }

    @Test
    void findByClientIdReturnsRegisteredClientWithGrantsAndScopes() {
        OAuthClientEntity entity = sampleEntity(true);
        when(jpaRepository.findByClientId("igrp-access-management")).thenReturn(Optional.of(entity));

        RegisteredClient rc = repository.findByClientId("igrp-access-management");

        assertNotNull(rc);
        assertEquals("igrp-access-management", rc.getClientId());
        assertTrue(rc.getAuthorizationGrantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS));
        assertTrue(rc.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(rc.getScopes().contains("openid"));
        assertTrue(rc.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC));
    }

    @Test
    void findByClientIdIgnoresInactiveClients() {
        OAuthClientEntity entity = sampleEntity(false);
        when(jpaRepository.findByClientId("igrp-access-management")).thenReturn(Optional.of(entity));

        RegisteredClient rc = repository.findByClientId("igrp-access-management");

        assertNull(rc, "Inactive client must not be exposed to the authorization server");
    }

    @Test
    void resolveGrantTypeMapsKnownTokens() {
        assertEquals(AuthorizationGrantType.CLIENT_CREDENTIALS,
                IgrpRegisteredClientRepository.resolveGrantType("client_credentials"));
        assertEquals(AuthorizationGrantType.REFRESH_TOKEN,
                IgrpRegisteredClientRepository.resolveGrantType("refresh_token"));
        assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE,
                IgrpRegisteredClientRepository.resolveGrantType("authorization_code"));
        assertEquals("custom", IgrpRegisteredClientRepository.resolveGrantType("custom").getValue());
    }

    private OAuthClientEntity sampleEntity(boolean active) {
        OAuthClientEntity e = new OAuthClientEntity();
        e.setId(UUID.randomUUID());
        e.setClientId("igrp-access-management");
        e.setClientSecret("{bcrypt}xxx");
        e.setClientName("iGRP");
        e.setActive(active);
        e.setAccessTokenTtl(3600);
        e.setRefreshTokenTtl(86400);
        e.setAuthorizationCodeTtl(300);
        e.setScopes(Set.of("openid", "profile", "email"));
        e.setGrantTypes(Set.of("authorization_code", "refresh_token", "client_credentials"));
        e.setRedirectUris(Set.of("http://localhost/callback"));
        return e;
    }
}
