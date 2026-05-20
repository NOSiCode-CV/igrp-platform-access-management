package cv.igrp.platform.access_management.m2m.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.springframework.security.oauth2.jwt.Jwt;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Synchronizes system permissions via machine-to-machine integration.
 * This service ensures that the permissions in the database match the list
 * received from the external system. The synchronization is idempotent.
 */
@Service
public class PermissionSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSyncService.class);

    private final AuthenticationHelper authenticationHelper;
    private final PermissionEntityRepository permissionRepository;
    private final ResourceEntityRepository resourceEntityRepository;
    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;

    public PermissionSyncService(PermissionEntityRepository permissionRepository,
                                 ResourceEntityRepository resourceEntityRepository,
                                 ObjectMapper objectMapper,
                                 AuthenticationHelper authenticationHelper,
                                 EventPublisher eventPublisher) {
        this.authenticationHelper = authenticationHelper;
        this.permissionRepository = permissionRepository;
        this.resourceEntityRepository = resourceEntityRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Synchronizes the permissions list with the database.
     * <p>
     * - Creates new permissions if they don't exist.<br>
     * - Updates permissions if they differ.<br>
     * - Deletes permissions that are no longer in the incoming list.<br>
     * - Ignores departmentCode, since M2M permissions are global.<br>
     * </p>
     *
     * @param permissions the list of permissions to synchronize
     */
    @Transactional
    public void synchronizePermissions(List<PermissionDTO> permissions, boolean isSystem) {
        if (permissions == null || permissions.isEmpty()) {
            LOGGER.warn("[PermissionSync] Received empty permission list, skipping synchronization.");
            return;
        }

        LOGGER.info("[PermissionSync] Starting synchronization for {} permissions", permissions.size());

        // Normalize input
        List<PermissionDTO> validPermissions = permissions.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.getName() != null && !dto.getName().isBlank())
                .collect(Collectors.toList());

        if (validPermissions.isEmpty()) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_PERMISSION_VALID_LIST_EMPTY);
        }

        // Get all existing permissions for the current resource. The resource
        // name was historically equal to the JWT sub because Spring AS issued
        // M2M tokens with sub == client_id. After the service-account refactor
        // sub is a service-account UUID, so we resolve the caller's resource
        // name from the SA -> OAuth client -> Application chain instead.
        String resourceName = isSystem ? "igrp-access-management" : resolveCallerResourceName();
        ResourceEntity resource = resourceEntityRepository
                .findByNameAndStatusNot(resourceName, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        IgrpErrorCode.IGRP_AUTH_RESOURCE_NOT_FOUND_BY_NAME, resourceName));

        Set<ResourceEntity> resourceEntities = new HashSet<>();
        resourceEntities.add(resource);

        List<PermissionEntity> existingPermissions = permissionRepository.findAllByResourcesAndStatusNot(resourceEntities, Status.DELETED);

        Map<String, PermissionEntity> existingByName = existingPermissions.stream()
                .collect(Collectors.toMap(
                        p -> p.getName().toLowerCase(Locale.ROOT),
                        p -> p
                ));

        Set<String> incomingNames = validPermissions.stream()
                .map(dto -> dto.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // Create or update incoming permissions
        for (PermissionDTO dto : validPermissions) {
            String key = dto.getName().toLowerCase(Locale.ROOT);
            PermissionEntity existing = existingByName.get(key);

            if (existing == null) {
                // Create new
                PermissionEntity newPerm = new PermissionEntity();
                newPerm.setName(dto.getName());
                newPerm.setDescription(dto.getDescription());
                newPerm.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
                PermissionEntity savedPerm = permissionRepository.save(newPerm);
                resource.getPermissions().add(savedPerm);
                LOGGER.info("[PermissionSync] Created new permission '{}'", dto.getName());
            } else {
                // Check for difference using structural hash
                String existingHash = computeStructuralHash(existing);
                String incomingHash = computeStructuralHash(dto);

                if (!existingHash.equals(incomingHash)) {
                    existing.setDescription(dto.getDescription());
                    existing.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
                    permissionRepository.save(existing);
                    LOGGER.info("[PermissionSync] Updated permission '{}'", dto.getName());
                } else {
                    LOGGER.debug("[PermissionSync] Permission '{}' already up to date", dto.getName());
                }
            }
        }

        resourceEntityRepository.save(resource);

        // Delete permissions not present in the incoming list
        List<PermissionEntity> toDelete = existingPermissions.stream()
                .filter(p -> !incomingNames.contains(p.getName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            for (PermissionEntity perm : toDelete) {
                perm.setStatus(Status.DELETED);
                permissionRepository.save(perm);
                LOGGER.info("[PermissionSync] Deleted permission '{}'", perm.getName());

                // Phase D / FR-16 cascade: publish so SessionInvalidationEventListener
                // can resolve every user that held this permission and revoke their
                // sessions, and so PermissionCacheInvalidator can evict cached entries.
                eventPublisher.publishPermissionDeleted(
                        new DeletePermissionEvent(this, perm.getName()));
            }
        }

        LOGGER.info("[PermissionSync] Synchronization completed successfully.");
    }

    /**
     * Resolve the resource name that identifies the calling M2M client.
     *
     * <p>The legacy code used {@code authenticationHelper.getSub()}: for
     * {@code client_credentials} tokens Spring AS set {@code sub} equal to
     * {@code client_id}, which by convention matched the resource name posted
     * earlier in {@code /sync/resources}. After the service-account refactor
     * {@code sub} holds the service-account UUID — never a resource name —
     * so we look up the resource name via the JWT {@code client_id} claim
     * (or {@code clientId} claim emitted by {@code ClaimsEnrichmentService}
     * for M2M tokens), preserving the original convention.
     *
     * <p>{@code ResourceEntity} has no FK back to the OAuth client, so this
     * is the only stable correlator available without changing the API
     * contract. Deployments where the resource is intentionally named
     * differently from the client_id need to sync a resource with
     * {@code name = <client_id>} (or extend the API to accept an explicit
     * resource identifier in the request body).
     */
    private String resolveCallerResourceName() {
        Jwt jwt = authenticationHelper.getJwtToken();

        String clientId = jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_CLIENT_ID);
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        // Pre-refactor M2M tokens: client_id wasn't surfaced as a claim, but
        // sub itself was the client_id. Fall back so older tokens still work.
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        return "<unknown-caller>";
    }

    private String computeStructuralHash(PermissionEntity entity) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("name", entity.getName());
            canonical.put("description", entity.getDescription());
            canonical.put("status", entity.getStatus());
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(canonical));
        } catch (Exception e) {
            throw new RuntimeException("Error computing hash for PermissionEntity", e);
        }
    }

    private String computeStructuralHash(PermissionDTO dto) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("name", dto.getName());
            canonical.put("description", dto.getDescription());
            canonical.put("status", dto.getStatus());
            return DigestUtils.sha256Hex(objectMapper.writeValueAsString(canonical));
        } catch (Exception e) {
            throw new RuntimeException("Error computing hash for PermissionDTO", e);
        }
    }
}