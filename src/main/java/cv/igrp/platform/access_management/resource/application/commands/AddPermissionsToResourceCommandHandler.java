package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles the addition of permissions to a {@link ResourceDTO}.
 * <p>
 * This command handler processes an {@link AddPermissionsToResourceCommand} by adding
 * specified permissions to a resource and returning the updated {@link ResourceDTO}.
 * </p>
 *
 * @see AddPermissionsToResourceCommand
 */
@Component
public class AddPermissionsToResourceCommandHandler implements CommandHandler<AddPermissionsToResourceCommand, ResponseEntity<ResourceDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddPermissionsToResourceCommandHandler.class);

    private final PermissionEntityRepository permissionEntityRepository;
    private final ResourceEntityRepository resourceEntityRepository;
    private final ResourceMapper resourceMapper;

    /**
     * Constructs an {@code AddPermissionsToResourceCommandHandler} with required dependencies.
     *
     * @param permissionEntityRepository the repository for accessing permissions
     * @param resourceEntityRepository   the repository for accessing resources
     * @param resourceMapper             the mapper for converting {@link ResourceEntity} to {@link ResourceDTO}
     */
    public AddPermissionsToResourceCommandHandler(
            PermissionEntityRepository permissionEntityRepository,
            ResourceEntityRepository resourceEntityRepository,
            ResourceMapper resourceMapper) {
        this.permissionEntityRepository = permissionEntityRepository;
        this.resourceEntityRepository = resourceEntityRepository;
        this.resourceMapper = resourceMapper;
    }

    /**
     * Handles the addition of permissions to a resource.
     *
     * @param command the command containing the resource ID and permissions to add
     * @return {@link ResponseEntity} with status 200 OK and the updated {@link ResourceDTO}
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<ResourceDTO> handle(AddPermissionsToResourceCommand command) {

        List<String> permissionList = command.getAddPermissionsToResourceRequest();

        List<PermissionEntity> permissions = permissionEntityRepository.findAllByNameIn(permissionList)
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .toList();

        if (permissions.isEmpty()) {
            LOGGER.warn("No valid permissions found to add to resource: {}", command.getName());
            throw IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Permission not found", permissions);
        }

        ResourceEntity resource = resourceEntityRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
                .orElseThrow(() -> {
                    LOGGER.warn("Resource not found with name: {}", command.getName());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Resource not found",
                            "Resource not found with name: " + command.getName());
                });

        resource.getPermissions().addAll(permissions);
        ResourceEntity updatedResource = resourceEntityRepository.save(resource);

        LOGGER.info("Added {} permissions to resource: {}", permissions.size(), command.getName());

        return ResponseEntity.ok(resourceMapper.toDto(updatedResource));

    }

}