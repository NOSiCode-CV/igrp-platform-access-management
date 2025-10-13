package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the removal of permissions from a resource.
 * <p>
 * This command handler processes a {@link RemovePermissionsFromResourceCommand} to remove
 * specified permissions from a resource and returns the updated {@link ResourceDTO}.
 * </p>
 *
 * @see RemovePermissionsFromResourceCommand
 * @see ResourceDTO
 */
@Component
public class RemovePermissionsFromResourceCommandHandler implements CommandHandler<RemovePermissionsFromResourceCommand, ResponseEntity<ResourceDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemovePermissionsFromResourceCommandHandler.class);

    private final ResourceEntityRepository resourceEntityRepository;
    private final ResourceMapper resourceMapper;

    /**
     * Constructs a {@code RemovePermissionsFromResourceCommandHandler} with required dependencies.
     *
     * @param resourceEntityRepository   the repository for accessing resources
     * @param resourceMapper             the mapper for converting between entities and DTOs
     */
    public RemovePermissionsFromResourceCommandHandler(
            ResourceEntityRepository resourceEntityRepository,
            ResourceMapper resourceMapper) {
        this.resourceEntityRepository = resourceEntityRepository;
        this.resourceMapper = resourceMapper;
    }

    /**
     * Handles the {@link RemovePermissionsFromResourceCommand} by removing the specified permissions
     * from the resource and returning the updated {@link ResourceDTO}.
     *
     * @param command the command containing the resource ID and permissions to remove
     * @return {@link ResponseEntity} with status 200 OK and the updated {@link ResourceDTO}
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<ResourceDTO> handle(RemovePermissionsFromResourceCommand command) {

        LOGGER.info("Remove Permissions From Resource Command Handler called with resource: {} and permissions: {}",
                command.getName(), command.getRemovePermissionsFromResourceRequest());

        ResourceEntity foundResource = resourceEntityRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
                .orElseThrow(() -> {
                    LOGGER.warn("Resource not found with code: {}", command.getName());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Resource not found",
                            "Resource not found with code: " + command.getName());
                });

        for (String permissionName : command.getRemovePermissionsFromResourceRequest()) {

            foundResource.getPermissions()
                    .stream()
                    .filter(permission -> permission.getName().equals(permissionName))
                    .findFirst()
                    .ifPresent(permission -> foundResource.getPermissions().remove(permission));

        }

        LOGGER.info("Remove Permissions From Resource called with resource: {}", foundResource.getName());

        ResourceEntity updatedResource = resourceEntityRepository.save(foundResource);
        ResourceDTO resourceDTO = resourceMapper.toDto(updatedResource);
        return ResponseEntity.ok(resourceDTO);

    }

}