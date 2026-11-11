package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.framework.auth.core.model.IssuedClient;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.idp.FederatedClients;
import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientRequestDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Management operations on persisted OAuth2 clients. The authorization server
 * reads directly from the same repository through
 * {@link cv.igrp.platform.access_management.oauth_server.infrastructure.security.IgrpRegisteredClientRepository}.
 */
@Service
public class OAuthClientService {

    private final OAuthClientJpaRepository repository;
    private final ApplicationEntityRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final FederatedClients federatedClients;

    public OAuthClientService(OAuthClientJpaRepository repository,
                              ApplicationEntityRepository applicationRepository,
                              PasswordEncoder passwordEncoder,
                              FederatedClients federatedClients) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
        this.federatedClients = federatedClients;
    }

    @Transactional(readOnly = true)
    public List<OAuthClientDTO> findAll() {
        return repository.findAll().stream().map(OAuthClientService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OAuthClientDTO findById(UUID id) {
        return toDto(require(id));
    }

    @Transactional
    public OAuthClientDTO create(OAuthClientRequestDTO request) {
        if (repository.existsByClientId(request.getClientId())) {
            throw new IllegalArgumentException("clientId already exists: " + request.getClientId());
        }
        if (FederatedClients.isServiceClient(normalizeGrantTypes(request.getGrantTypes()))) {
            return createFederated(request);
        }

        String rawSecret = generateClientSecret();

        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(request.getClientId());
        entity.setClientSecret(passwordEncoder.encode(rawSecret));
        applyRequestOntoEntity(request, entity);

        OAuthClientEntity saved = repository.save(entity);
        OAuthClientDTO dto = toDto(saved);
        // Expose the raw secret exactly once, at creation.
        dto.setClientSecret(rawSecret);
        return dto;
    }

    /**
     * A service client (v3-17): created in the IdP, which generates and keeps the secret.
     * AM stores the client without a secret and returns the IdP's secret exactly once.
     * If AM cannot store the client, the IdP's client is deleted again.
     */
    private OAuthClientDTO createFederated(OAuthClientRequestDTO request) {
        IssuedClient issued = federatedClients.create(request.getClientId(), request.getClientName());
        OAuthClientEntity saved;
        try {
            OAuthClientEntity entity = new OAuthClientEntity();
            entity.setId(UUID.randomUUID());
            entity.setClientId(issued.clientId());
            entity.setIdp(federatedClients.idp());
            applyRequestOntoEntity(request, entity);
            saved = repository.saveAndFlush(entity);
        } catch (RuntimeException ex) {
            try {
                federatedClients.delete(issued.clientId());
            } catch (RuntimeException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }
        OAuthClientDTO dto = toDto(saved);
        dto.setClientSecret(issued.clientSecret());
        return dto;
    }

    @Transactional
    public OAuthClientDTO update(UUID id, OAuthClientRequestDTO request) {
        OAuthClientEntity entity = require(id);
        boolean federated = entity.getIdp() != null;
        if (federated && !FederatedClients.isServiceClient(normalizeGrantTypes(request.getGrantTypes()))) {
            throw new IllegalArgumentException(
                    "A service client federated in the IdP can only have the client_credentials grant: "
                            + entity.getClientId());
        }
        applyRequestOntoEntity(request, entity);
        OAuthClientEntity saved = repository.save(entity);
        if (federated) {
            federatedClients.update(entity.getClientId(), entity.getClientName());
        }
        return toDto(saved);
    }

    /**
     * Replace this client's secret with a freshly generated one, returning the
     * raw value exactly once — the same contract as {@link #create}.
     *
     * <p>Exists so a leaked secret can be replaced without the delete-and-
     * recreate dance, which is not atomic (a failed re-create leaves no client
     * at all) and which changes the {@code id}, orphaning every downstream
     * reference to it. Here {@code id} and {@code clientId} are preserved, so
     * consumers only need the new secret.
     *
     * <p>Effective immediately: {@code IgrpRegisteredClientRepository} reads
     * the entity on every lookup and holds no cache, so the previous secret
     * stops authenticating as soon as this transaction commits.
     *
     * <p><b>Access tokens already issued to this client stay valid until they
     * expire</b> — they are self-contained JWTs verified by signature, not by
     * re-checking the client secret. Rotation closes the door on obtaining
     * NEW tokens. If a leak requires cutting off existing ones too, deactivate
     * the client (or shorten its access-token TTL) as well.
     */
    @Transactional
    public OAuthClientDTO rotateSecret(UUID id) {
        OAuthClientEntity entity = require(id);
        if (entity.getIdp() != null) {
            OAuthClientDTO dto = toDto(entity);
            dto.setClientSecret(federatedClients.rotateSecret(entity.getClientId()).clientSecret());
            return dto;
        }

        String rawSecret = generateClientSecret();
        entity.setClientSecret(passwordEncoder.encode(rawSecret));

        OAuthClientDTO dto = toDto(repository.save(entity));
        // Exposed once, exactly as on creation. Never recoverable afterwards.
        dto.setClientSecret(rawSecret);
        return dto;
    }

    @Transactional
    public void delete(UUID id) {
        OAuthClientEntity entity = require(id);
        repository.delete(entity);
        if (entity.getIdp() != null) {
            // Flush first: a bound service account makes AM refuse (409) before the IdP loses the client.
            repository.flush();
            federatedClients.delete(entity.getClientId());
        }
    }

    private OAuthClientEntity require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("OAuthClient not found: " + id));
    }

    private void applyRequestOntoEntity(OAuthClientRequestDTO request, OAuthClientEntity entity) {
        entity.setClientName(request.getClientName());
        entity.setDescription(request.getDescription());
        entity.setActive(request.isActive());
        // PKCE toggle — only override the entity when the caller sent a
        // value, so partial PUTs that omit requirePkce keep the existing
        // setting (OWASP A01 default: true).
        if (request.getRequirePkce() != null) {
            entity.setRequirePkce(request.getRequirePkce());
        }
        entity.setAccessTokenTtl(request.getAccessTokenTtl());
        entity.setRefreshTokenTtl(request.getRefreshTokenTtl());
        entity.setAuthorizationCodeTtl(request.getAuthorizationCodeTtl());
        entity.setScopes(request.getScopes() == null ? new HashSet<>() : new HashSet<>(request.getScopes()));
        entity.setRedirectUris(request.getRedirectUris() == null ? new HashSet<>() : new HashSet<>(request.getRedirectUris()));
        entity.setPostLogoutRedirectUris(request.getPostLogoutRedirectUris() == null ? new HashSet<>() : new HashSet<>(request.getPostLogoutRedirectUris()));
        entity.setGrantTypes(normalizeGrantTypes(request.getGrantTypes()));
        if (request.getApplicationId() != null) {
            ApplicationEntity app = applicationRepository.findById(request.getApplicationId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Application not found: " + request.getApplicationId()));
            entity.setApplication(app);
        } else {
            entity.setApplication(null);
        }
    }

    private Set<String> normalizeGrantTypes(Set<String> grantTypes) {
        if (grantTypes == null) {
            return new HashSet<>();
        }
        Set<String> normalized = new HashSet<>();
        for (String grant : grantTypes) {
            if (grant == null || grant.isBlank()) {
                continue;
            }
            normalized.add(grant.toLowerCase());
        }
        return normalized;
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static OAuthClientDTO toDto(OAuthClientEntity e) {
        return OAuthClientDTO.builder()
                .id(e.getId())
                .clientId(e.getClientId())
                .clientName(e.getClientName())
                .description(e.getDescription())
                .active(e.isActive())
                .requirePkce(e.isRequirePkce())
                .applicationId(e.getApplication() != null ? e.getApplication().getId() : null)
                .applicationCode(e.getApplication() != null ? e.getApplication().getCode() : null)
                .accessTokenTtl(e.getAccessTokenTtl())
                .refreshTokenTtl(e.getRefreshTokenTtl())
                .authorizationCodeTtl(e.getAuthorizationCodeTtl())
                .scopes(e.getScopes())
                .redirectUris(e.getRedirectUris())
                .postLogoutRedirectUris(e.getPostLogoutRedirectUris())
                .grantTypes(e.getGrantTypes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
