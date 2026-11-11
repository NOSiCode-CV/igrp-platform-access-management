package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.framework.auth.core.model.IssuedClient;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientRequestDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.idp.FederatedClients;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** v3-17: service clients through {@link OAuthClientService}; the IdP keeps the secret, AM never does. */
class OAuthClientServiceFederatedTest {

    private final OAuthClientJpaRepository repository = mock(OAuthClientJpaRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final FederatedClients federated = mock(FederatedClients.class);
    private final OAuthClientService service = new OAuthClientService(repository,
            mock(ApplicationEntityRepository.class), passwordEncoder, federated);

    private static OAuthClientRequestDTO request(String clientId, Set<String> grants) {
        return OAuthClientRequestDTO.builder().clientId(clientId).clientName("Faturação API").active(true)
                .accessTokenTtl(3600).refreshTokenTtl(86400).authorizationCodeTtl(300)
                .scopes(Set.of("openid")).grantTypes(grants).build();
    }

    private static OAuthClientEntity federatedEntity() {
        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId("faturacao-api");
        entity.setClientName("Faturação API");
        entity.setIdp("keycloak");
        entity.setGrantTypes(Set.of("client_credentials"));
        return entity;
    }

    @Test
    @DisplayName("Create: the client is made in the IdP, stored without a secret, and the IdP's secret is returned once")
    void create() {
        when(federated.create("faturacao-api", "Faturação API"))
                .thenReturn(new IssuedClient("faturacao-api", "idp-secret", null));
        when(federated.idp()).thenReturn("keycloak");
        when(repository.saveAndFlush(any(OAuthClientEntity.class))).thenAnswer(call -> call.getArgument(0));

        OAuthClientDTO created = service.create(request("faturacao-api", Set.of("CLIENT_CREDENTIALS")));

        assertEquals("idp-secret", created.getClientSecret());
        ArgumentCaptor<OAuthClientEntity> stored = ArgumentCaptor.forClass(OAuthClientEntity.class);
        verify(repository).saveAndFlush(stored.capture());
        assertNull(stored.getValue().getClientSecret());
        assertEquals("keycloak", stored.getValue().getIdp());
        assertEquals("faturacao-api", stored.getValue().getClientId());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Create: if AM cannot store the client, the IdP's client is deleted again")
    void createCompensates() {
        when(federated.create(any(), any())).thenReturn(new IssuedClient("faturacao-api", "s", null));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        RuntimeException cleanupFailure = new IllegalStateException("IdP down");
        doThrow(cleanupFailure).when(federated).delete("faturacao-api");

        OAuthClientRequestDTO request = request("faturacao-api", Set.of("client_credentials"));
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.create(request));
        assertSame(failure, thrown);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
    }

    @Test
    @DisplayName("Update: name changes reach the IdP; a federated client cannot gain other grants")
    void update() {
        OAuthClientEntity entity = federatedEntity();
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        OAuthClientRequestDTO renamed = request("faturacao-api", Set.of("client_credentials"));
        renamed.setClientName("Faturação API v2");
        service.update(entity.getId(), renamed);
        verify(federated).update("faturacao-api", "Faturação API v2");

        OAuthClientRequestDTO login = request("faturacao-api", Set.of("client_credentials", "authorization_code"));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.update(entity.getId(), login));
        assertTrue(refused.getMessage().contains("client_credentials"), refused.getMessage());
    }

    @Test
    @DisplayName("Rotate: the IdP makes the new secret; AM's row is not touched")
    void rotate() {
        OAuthClientEntity entity = federatedEntity();
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(federated.rotateSecret("faturacao-api")).thenReturn(new IssuedClient("faturacao-api", "new-secret", null));

        OAuthClientDTO rotated = service.rotateSecret(entity.getId());

        assertEquals("new-secret", rotated.getClientSecret());
        assertNull(entity.getClientSecret());
        verify(repository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Delete: AM's row first (a bound service account stops it), then the IdP's client")
    void delete() {
        OAuthClientEntity entity = federatedEntity();
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.delete(entity.getId());

        InOrder order = inOrder(repository, federated);
        order.verify(repository).delete(entity);
        order.verify(repository).flush();
        order.verify(federated).delete("faturacao-api");
    }

    @Test
    @DisplayName("Delete: an AM-local client never reaches the IdP")
    void deleteLocal() {
        OAuthClientEntity entity = federatedEntity();
        entity.setIdp(null);
        entity.setClientSecret("{bcrypt}hash");
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.delete(entity.getId());

        verify(repository).delete(entity);
        verifyNoInteractions(federated);
    }
}
