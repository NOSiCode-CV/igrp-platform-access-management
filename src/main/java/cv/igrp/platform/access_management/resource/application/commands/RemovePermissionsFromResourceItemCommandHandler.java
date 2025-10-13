package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the removal of permissions from a specific resource item.
 * <p>
 * This command handler processes the {@link RemovePermissionsFromResourceItemCommand}
 * by removing the specified permissions from the resource item identified by its ID.
 * The updated {@link ResourceItemDTO} is returned in the response.
 * </p>
 *
 * @see RemovePermissionsFromResourceItemCommand
 * @see ResourceItemDTO
 */
@Component
public class RemovePermissionsFromResourceItemCommandHandler implements CommandHandler<RemovePermissionsFromResourceItemCommand, ResponseEntity<ResourceItemDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemovePermissionsFromResourceItemCommandHandler.class);

    private final ResourceItemEntityRepository resourceItemEntityRepository;
    private final ResourceMapper resourceMapper;

    /**
     * Constructs a {@code RemovePermissionsFromResourceItemCommandHandler} with required dependencies.
     *
     * @param resourceItemEntityRepository the repository for accessing resource items
     * @param resourceMapper               the mapper for converting between entities and DTOs
     */
    public RemovePermissionsFromResourceItemCommandHandler(
            ResourceItemEntityRepository resourceItemEntityRepository,
            ResourceMapper resourceMapper) {
        this.resourceItemEntityRepository = resourceItemEntityRepository;
        this.resourceMapper = resourceMapper;
    }

    /**
     * Handles the {@link RemovePermissionsFromResourceItemCommand} by removing the specified permissions
     * from the resource item and returning the updated {@link ResourceItemDTO}.
     *
     * @param command the command containing the resource item ID and permissions to remove
     * @return {@link ResponseEntity} with status 200 OK and the updated {@link ResourceItemDTO}
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<ResourceItemDTO> handle(RemovePermissionsFromResourceItemCommand command) {

        LOGGER.info("Remove permissions {} from resource item with ID: {}", command.getRemovePermissionsFromResourceItemRequest(), command.getName());

        ResourceItemEntity foundResourceItem = resourceItemEntityRepository.findByName(command.getName())
                .orElseThrow(() -> {
                    LOGGER.warn("Resource item not found with name: {}", command.getName());
                    return IgrpResponseStatusException.notFound("Resource item not found with name: " + command.getName());
                });

        for (String permissions : command.getRemovePermissionsFromResourceItemRequest()) {
            foundResourceItem.getPermissions()
                    .stream()
                    .filter(permission -> permission.getName().equals(permissions)
                    ).findFirst()
                    .ifPresent(permission -> foundResourceItem.getPermissions().remove(permission));
        }

        LOGGER.info("Removed permissions from resource item: {}", command.getName());

        ResourceItemEntity updatedResourceItem = resourceItemEntityRepository.save(foundResourceItem);
        ResourceItemDTO resourceItemDTO = resourceMapper.toItemDto(updatedResourceItem);
        return ResponseEntity.ok(resourceItemDTO);

    }

}