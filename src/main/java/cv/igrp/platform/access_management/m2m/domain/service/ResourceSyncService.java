package cv.igrp.platform.access_management.m2m.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.department.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ResourceSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ResourceSyncService.class);

    private final ResourceEntityRepository resourceRepository;
    private final ResourceItemEntityRepository resourceItemEntityRepository;
    private final PermissionEntityRepository permissionRepository;
    private final ResourceMapper resourceMapper;

    public ResourceSyncService(ResourceEntityRepository resourceRepository,
                               ResourceItemEntityRepository resourceItemEntityRepository,
                               PermissionEntityRepository permissionRepository,
                               ResourceMapper resourceMapper) {
        this.resourceRepository = resourceRepository;
        this.resourceItemEntityRepository = resourceItemEntityRepository;
        this.permissionRepository = permissionRepository;
        this.resourceMapper = resourceMapper;
    }

    @Transactional
    public void synchronizeResource(ResourceDTO resourceDTO) {
        logger.info("[ResourceSync] Starting synchronization for resource '{}'", resourceDTO.getName());

        ResourceEntity existing = resourceRepository.findByNameAndStatusNot(resourceDTO.getName(), Status.DELETED).orElse(null);

        String incomingHash = computeStructuralHash(resourceDTO);

        if (existing != null) {
            String existingHash = computeStructuralHash(resourceMapper.toDto(existing));
            if (incomingHash.equals(existingHash)) {
                logger.info("[ResourceSync] Resource '{}' already up-to-date.", resourceDTO.getName());
                return;
            }

            logger.info("[ResourceSync] Updating existing resource '{}'", resourceDTO.getName());
            updateResource(existing, resourceDTO);
        } else {
            logger.info("[ResourceSync] Creating new resource '{}'", resourceDTO.getName());
            ResourceEntity entity = resourceMapper.toEntity(resourceDTO);
            resourceRepository.save(entity);
        }
    }

    private void updateResource(ResourceEntity existing, ResourceDTO dto) {
        existing.setDescription(dto.getDescription());

        if(dto.getStatus() != null)
            existing.setStatus(dto.getStatus());

        // Update items
        if (dto.getItems() != null) {
            Set<String> incomingNames = dto.getItems().stream().map(ResourceItemDTO::getName).collect(Collectors.toSet());
            for (ResourceItemEntity item : existing.getItems()) {
                if (!incomingNames.contains(item.getName())) {
                    resourceItemEntityRepository.deleteById(item.getId());
                }
            }

            for (ResourceItemDTO itemDto : dto.getItems()) {
                existing.getItems().stream()
                        .filter(it -> it.getName().equals(itemDto.getName()))
                        .findFirst()
                        .ifPresentOrElse(
                                it -> {
                                    var permissions = permissionRepository.findAllByNameIn(itemDto.getPermissions());
                                    var permissionsToAdd = permissions.stream().filter(permission -> !it.getPermissions().contains(permission)).toList();
                                    it.getPermissions().addAll(permissionsToAdd);
                                    it.getPermissions().removeIf(permission -> !incomingNames.contains(permission.getName()));
                                },
                                () -> existing.getItems().add(resourceMapper.toItemEntity(itemDto, existing))
                        );
            }
        }

        resourceRepository.save(existing);
    }

    private String computeStructuralHash(ResourceDTO dto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String canonicalJson = mapper.writeValueAsString(dto);
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new RuntimeException("Error computing resource hash", e);
        }
    }
}