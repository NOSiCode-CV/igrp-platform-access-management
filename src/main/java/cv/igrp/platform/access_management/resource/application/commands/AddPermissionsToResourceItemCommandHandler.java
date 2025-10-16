package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles the addition of permissions to a resource item.
 * <p>
 * This command handler processes the {@link AddPermissionsToResourceItemCommand}
 * by adding the specified permissions to the resource item and returning the updated
 * {@link ResourceItemDTO}. The actual implementation logic for adding permissions
 * should be implemented in the {@code handle} method.
 * </p>
 *
 * @see AddPermissionsToResourceItemCommand
 * @see ResourceItemDTO
 */
@Component
public class AddPermissionsToResourceItemCommandHandler implements CommandHandler<AddPermissionsToResourceItemCommand, ResponseEntity<ResourceItemDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddPermissionsToResourceItemCommandHandler.class);

    private final PermissionEntityRepository permissionEntityRepository;
    private final ResourceItemEntityRepository resourceItemEntityRepository;
    private final ResourceMapper resourceMapper;

    /**
     * Constructs an {@code AddPermissionsToResourceItemCommandHandler} with required dependencies.
     *
     * @param permissionEntityRepository   the repository for accessing permission entities
     * @param resourceItemEntityRepository the repository for accessing resource item entities
     * @param resourceMapper               the mapper for converting between domain models and DTOs
     */
    public AddPermissionsToResourceItemCommandHandler(PermissionEntityRepository permissionEntityRepository,
                                                      ResourceItemEntityRepository resourceItemEntityRepository,
                                                      ResourceMapper resourceMapper) {
        this.permissionEntityRepository = permissionEntityRepository;
        this.resourceItemEntityRepository = resourceItemEntityRepository;
        this.resourceMapper = resourceMapper;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<ResourceItemDTO> handle(AddPermissionsToResourceItemCommand command) {

        LOGGER.info("Adding permissions {} to resource item with name: {}", command.getAddPermissionsToResourceItemRequest(), command.getName());

        List<String> permissionNames = command.getAddPermissionsToResourceItemRequest();

        ResourceItemEntity resourceItemEntity = resourceItemEntityRepository.findByName(command.getName())
                .orElseThrow(() -> {
                    LOGGER.warn("Resource item not found with name: {}", command.getName());
                    return IgrpResponseStatusException.notFound("Resource item not found with name: " + command.getName());
                });

        List<PermissionEntity> permissions = permissionEntityRepository.findAllByNameIn(permissionNames)
                .stream()
                .filter(permissionEntity -> !permissionEntity.getStatus().equals(Status.DELETED) && resourceItemEntity.getResourceId().getPermissions().contains(permissionEntity))
                .toList();

        if (permissions.isEmpty()) {
            LOGGER.warn("No valid permissions found to add to resource item with name: {}", command.getName());
            throw IgrpResponseStatusException.notFound("No valid permissions found to add to resource item with name: " + command.getName());
        }

        resourceItemEntity.getPermissions().addAll(permissions);

        ResourceItemEntity updatedResourceItem = resourceItemEntityRepository.save(resourceItemEntity);
        LOGGER.info("Permissions added successfully to resource item with name: {}", command.getName());
        return ResponseEntity.ok(resourceMapper.toItemDto(updatedResourceItem));

    }

}