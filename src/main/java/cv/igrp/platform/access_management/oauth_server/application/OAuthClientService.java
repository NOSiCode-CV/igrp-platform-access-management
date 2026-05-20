package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.platform.access_management.oauth_server.application.dto.OAuthClientDTO;
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

    public OAuthClientService(OAuthClientJpaRepository repository,
                              ApplicationEntityRepository applicationRepository,
                              PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
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

    @Transactional
    public OAuthClientDTO update(UUID id, OAuthClientRequestDTO request) {
        OAuthClientEntity entity = require(id);
        applyRequestOntoEntity(request, entity);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        OAuthClientEntity entity = require(id);
        repository.delete(entity);
    }

    private OAuthClientEntity require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("OAuthClient not found: " + id));
    }

    private void applyRequestOntoEntity(OAuthClientRequestDTO request, OAuthClientEntity entity) {
        entity.setClientName(request.getClientName());
        entity.setDescription(request.getDescription());
        entity.setActive(request.isActive());
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
