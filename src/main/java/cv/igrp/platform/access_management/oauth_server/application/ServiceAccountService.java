package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountRequestDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ServiceAccountService {

    private final ServiceAccountJpaRepository repository;
    private final OAuthClientJpaRepository oauthClientRepository;
    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleRepository;
    private final PermissionEntityRepository permissionRepository;

    public ServiceAccountService(ServiceAccountJpaRepository repository,
                                 OAuthClientJpaRepository oauthClientRepository,
                                 ApplicationEntityRepository applicationRepository,
                                 RoleEntityRepository roleRepository,
                                 PermissionEntityRepository permissionRepository) {
        this.repository = repository;
        this.oauthClientRepository = oauthClientRepository;
        this.applicationRepository = applicationRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceAccountDTO> findAll() {
        return repository.findAll().stream()
                .map(ServiceAccountService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceAccountDTO findById(UUID id) {
        return toDto(requireWithRoles(id));
    }

    @Transactional
    public ServiceAccountDTO create(ServiceAccountRequestDTO request) {
        if (repository.existsByOauthClient_Id(request.getOauthClientId())) {
            throw new IllegalArgumentException(
                    "oauthClientId already has a service account: " + request.getOauthClientId());
        }

        ServiceAccountEntity entity = new ServiceAccountEntity();
        entity.setId(UUID.randomUUID());
        applyRequestOntoEntity(request, entity);
        return toDto(repository.save(entity));
    }

    @Transactional
    public ServiceAccountDTO update(UUID id, ServiceAccountRequestDTO request) {
        ServiceAccountEntity entity = requireWithRoles(id);
        if (!entity.getOauthClient().getId().equals(request.getOauthClientId())
                && repository.existsByOauthClient_Id(request.getOauthClientId())) {
            throw new IllegalArgumentException(
                    "oauthClientId already has a service account: " + request.getOauthClientId());
        }

        applyRequestOntoEntity(request, entity);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(requireWithRoles(id));
    }

    private ServiceAccountEntity requireWithRoles(UUID id) {
        return repository.findByIdWithRolesAndPermissions(id)
                .orElseThrow(() -> new EntityNotFoundException("ServiceAccount not found: " + id));
    }

    private void applyRequestOntoEntity(ServiceAccountRequestDTO request, ServiceAccountEntity entity) {
        OAuthClientEntity oauthClient = oauthClientRepository.findById(request.getOauthClientId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "OAuthClient not found: " + request.getOauthClientId()));

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setActive(request.isActive());
        entity.setOauthClient(oauthClient);
        entity.setApplication(resolveApplication(request.getApplicationId(), oauthClient));
        entity.replaceRoleAssignments(resolveRoles(request.getRoleIds()));
        entity.replacePermissionGrants(resolvePermissions(request.getPermissionIds()));
    }

    private ApplicationEntity resolveApplication(Integer applicationId, OAuthClientEntity oauthClient) {
        if (applicationId != null) {
            return applicationRepository.findById(applicationId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Application not found: " + applicationId));
        }
        return oauthClient.getApplication();
    }

    private Set<RoleEntity> resolveRoles(Set<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<RoleEntity> roles = roleRepository.findAllById(roleIds);
        Set<Integer> found = roles.stream().map(RoleEntity::getId).collect(Collectors.toSet());
        Set<Integer> missing = roleIds.stream()
                .filter(id -> !found.contains(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException("Role not found: " + missing);
        }
        return new HashSet<>(roles);
    }

    private Set<PermissionEntity> resolvePermissions(Set<Integer> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        List<PermissionEntity> permissions = permissionRepository.findAllById(permissionIds);
        Set<Integer> found = permissions.stream().map(PermissionEntity::getId).collect(Collectors.toSet());
        Set<Integer> missing = permissionIds.stream()
                .filter(id -> !found.contains(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException("Permission not found: " + missing);
        }
        return new HashSet<>(permissions);
    }

    public static ServiceAccountDTO toDto(ServiceAccountEntity entity) {
        Set<RoleEntity> roles = entity.getRoles() != null ? entity.getRoles() : Collections.emptySet();
        Set<PermissionEntity> directPermissions = entity.getDirectPermissions() != null
                ? entity.getDirectPermissions()
                : Collections.emptySet();
        return ServiceAccountDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.isActive())
                .oauthClientId(entity.getOauthClient() != null ? entity.getOauthClient().getId() : null)
                .clientId(entity.getOauthClient() != null ? entity.getOauthClient().getClientId() : null)
                .applicationId(entity.getApplication() != null ? entity.getApplication().getId() : null)
                .applicationCode(entity.getApplication() != null ? entity.getApplication().getCode() : null)
                .roleIds(roles.stream().map(RoleEntity::getId).collect(Collectors.toSet()))
                .roleCodes(roles.stream().map(RoleEntity::getCode).collect(Collectors.toSet()))
                .permissionIds(directPermissions.stream().map(PermissionEntity::getId).collect(Collectors.toSet()))
                .permissionNames(directPermissions.stream().map(PermissionEntity::getName).collect(Collectors.toSet()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
