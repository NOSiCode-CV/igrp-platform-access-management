package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientRequestDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthClientServiceTest {

    @Mock private OAuthClientJpaRepository repository;
    @Mock private ApplicationEntityRepository applicationRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private OAuthClientService service;

    @BeforeEach
    void setUp() {
        service = new OAuthClientService(repository, applicationRepository, passwordEncoder);
    }

    @Test
    void createGeneratesAndReturnsRawSecretExactlyOnce() {
        OAuthClientRequestDTO req = OAuthClientRequestDTO.builder()
                .clientId("acme")
                .clientName("Acme")
                .active(true)
                .accessTokenTtl(3600)
                .refreshTokenTtl(86400)
                .authorizationCodeTtl(300)
                .scopes(new HashSet<>(Set.of("openid")))
                .grantTypes(new HashSet<>(Set.of("client_credentials")))
                .build();

        when(repository.existsByClientId("acme")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}encoded");
        when(repository.save(any(OAuthClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthClientDTO created = service.create(req);

        assertNotNull(created.getClientSecret(), "raw secret must be returned once on creation");
        assertEquals("acme", created.getClientId());
        assertTrue(created.isActive());
        assertTrue(created.getGrantTypes().contains("client_credentials"));

        ArgumentCaptor<OAuthClientEntity> captor = ArgumentCaptor.forClass(OAuthClientEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("{bcrypt}encoded", captor.getValue().getClientSecret(),
                "persisted secret must be encoded, not raw");
    }

    @Test
    void createRejectsDuplicateClientId() {
        OAuthClientRequestDTO req = OAuthClientRequestDTO.builder()
                .clientId("dup")
                .clientName("dup")
                .scopes(Set.of("openid"))
                .grantTypes(Set.of("client_credentials"))
                .build();
        when(repository.existsByClientId("dup")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void updateAttachesApplicationWhenProvided() {
        UUID id = UUID.randomUUID();
        OAuthClientEntity existing = new OAuthClientEntity();
        existing.setId(id);
        existing.setClientId("acme");
        existing.setClientSecret("{bcrypt}x");

        ApplicationEntity app = new ApplicationEntity();
        app.setId(55);
        app.setCode("APP_55");

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(applicationRepository.findById(55)).thenReturn(Optional.of(app));
        when(repository.save(any(OAuthClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthClientRequestDTO req = OAuthClientRequestDTO.builder()
                .clientId("acme")
                .clientName("Acme")
                .applicationId(55)
                .active(true)
                .accessTokenTtl(3600)
                .refreshTokenTtl(86400)
                .authorizationCodeTtl(300)
                .scopes(Set.of("openid"))
                .grantTypes(Set.of("client_credentials"))
                .build();

        OAuthClientDTO updated = service.update(id, req);
        assertEquals(55, updated.getApplicationId());
        assertEquals("APP_55", updated.getApplicationCode());
    }

    @Test
    void rotateSecretReturnsRawOnceAndPersistsOnlyTheHash() {
        UUID id = UUID.randomUUID();
        OAuthClientEntity existing = new OAuthClientEntity();
        existing.setId(id);
        existing.setClientId("acme");
        existing.setClientSecret("{bcrypt}OLD");

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}NEW");
        when(repository.save(any(OAuthClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthClientDTO rotated = service.rotateSecret(id);

        assertNotNull(rotated.getClientSecret(), "raw secret must be returned once on rotation");
        assertNotEquals("{bcrypt}NEW", rotated.getClientSecret(), "the DTO must carry the RAW secret, not the hash");

        ArgumentCaptor<OAuthClientEntity> captor = ArgumentCaptor.forClass(OAuthClientEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("{bcrypt}NEW", captor.getValue().getClientSecret(),
                "persisted secret must be the new hash, never the raw value");
    }

    @Test
    void rotateSecretPreservesIdentity() {
        // The whole point of the endpoint: delete-and-recreate changes the id
        // and orphans downstream references. Rotation must not.
        UUID id = UUID.randomUUID();
        OAuthClientEntity existing = new OAuthClientEntity();
        existing.setId(id);
        existing.setClientId("acme");
        existing.setClientSecret("{bcrypt}OLD");

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}NEW");
        when(repository.save(any(OAuthClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthClientDTO rotated = service.rotateSecret(id);

        assertEquals(id, rotated.getId());
        assertEquals("acme", rotated.getClientId());
        verify(repository, never()).delete(any(OAuthClientEntity.class));
    }

    @Test
    void rotateSecretProducesADifferentSecretEachTime() {
        UUID id = UUID.randomUUID();
        OAuthClientEntity existing = new OAuthClientEntity();
        existing.setId(id);
        existing.setClientId("acme");

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenAnswer(inv -> "{bcrypt}" + inv.getArgument(0));
        when(repository.save(any(OAuthClientEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String first = service.rotateSecret(id).getClientSecret();
        String second = service.rotateSecret(id).getClientSecret();

        assertNotEquals(first, second);
    }

    @Test
    void rotateSecretThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.rotateSecret(id));
        verify(repository, never()).save(any(OAuthClientEntity.class));
    }

    @Test
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        OAuthClientRequestDTO req = OAuthClientRequestDTO.builder()
                .clientId("c").clientName("c")
                .scopes(Set.of("openid")).grantTypes(Set.of("client_credentials"))
                .build();
        assertThrows(EntityNotFoundException.class, () -> service.update(id, req));
    }
}
