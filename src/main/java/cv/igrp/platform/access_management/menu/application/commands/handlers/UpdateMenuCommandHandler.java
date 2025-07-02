package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.commands.commands.UpdateMenuCommand;

/**
 * Command handler responsible for processing {@link UpdateMenuCommand} requests to update
 * existing {@link MenuEntry} records.
 * <p>
 * This handler updates core attributes (such as name, type, icon, and status) and validates
 * related references including parent menus, resources, and applications. If any of the related
 * entities are not found, it throws an {@link IgrpResponseStatusException} with the appropriate
 * HTTP status and descriptive error message.
 * </p>
 *
 */
@Service
public class UpdateMenuCommandHandler implements CommandHandler<UpdateMenuCommand, ResponseEntity<MenuEntryDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateMenuCommandHandler.class);

    private final MenuEntryRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;
    private final ApplicationRepository applicationRepository;
    private final ResourceRepository resourceRepository;

    /**
     * Constructs an {@code UpdateMenuCommandHandler} with required dependencies.
     *
     * @param menuEntryRepository the repository used to access and persist {@link MenuEntry} entities
     * @param menuEntryMapper the mapper used to convert between {@link MenuEntry} and {@link MenuEntryDTO}
     * @param applicationRepository the repository used to validate and retrieve associated {@link cv.igrp.platform.access_management.shared.domain.models.Application} entities
     * @param resourceRepository the repository used to validate and retrieve associated {@link cv.igrp.platform.access_management.shared.domain.models.Resource} entities
     */
    public UpdateMenuCommandHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationRepository applicationRepository, ResourceRepository resourceRepository) {
        this.menuEntryRepository = menuEntryRepository;
        this.applicationRepository = applicationRepository;
        this.resourceRepository = resourceRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    /**
     * Handles the {@link UpdateMenuCommand} by updating the corresponding {@link MenuEntry}
     * with values from the provided {@link MenuEntryDTO}.
     * <p>
     * It validates the existence of the menu entry, and optionally resolves and verifies foreign key
     * relationships for parent menu, resource, and application if the corresponding IDs are provided.
     * </p>
     *
     * @param command the command containing the menu ID and updated data
     * @return {@link ResponseEntity} with status 200 OK and the updated {@link MenuEntryDTO}
     * @throws IgrpResponseStatusException if the menu, parent, resource, or application is not found
     */
    @IgrpCommandHandler
    public ResponseEntity<MenuEntryDTO> handle(UpdateMenuCommand command) {

        MenuEntry menuEntry = menuEntryRepository.findById(command.getId())
                .orElseThrow(() -> {
                    logger.warn("Menu not found with ID: {}", command.getId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                                    "Menu not found",
                                    "Menu not found with id: " + command.getId());
                });

        MenuEntryDTO menuDto = command.getMenuentrydto();

        menuEntry.setName(menuDto.getName());
        menuEntry.setType(menuDto.getType());
        menuEntry.setPosition(menuDto.getPosition());
        menuEntry.setIcon(menuDto.getIcon());
        menuEntry.setStatus(menuDto.getStatus());
        menuEntry.setTarget(menuDto.getTarget());
        menuEntry.setUrl(menuDto.getUrl());

        if (menuDto.getParentId() != null) {
            menuEntry.setParentId(menuEntryRepository.findById(menuDto.getParentId())
                    .orElseThrow(() -> {
                        logger.warn("Parent Menu not found with ID: {}", menuDto.getParentId());
                        return IgrpResponseStatusException.of(
                                HttpStatus.NOT_FOUND,
                                "Parent MenuEntry not found",
                                "Parent MenuEntry not found with id: " + menuDto.getParentId());
                    }));
        }

        if (menuDto.getResourceId() != null) {
            menuEntry.setResourceId(resourceRepository.findById(menuDto.getResourceId())
                    .orElseThrow(() -> {
                        logger.warn("Resource not found with ID: {}", menuDto.getResourceId());
                        return IgrpResponseStatusException.of(
                                HttpStatus.NOT_FOUND,
                                "Resource not found",
                                "Resource not found with id: " + menuDto.getResourceId());
                    }));
        }

        if (menuDto.getApplicationId() != null){
            menuEntry.setApplicationId(applicationRepository.findById(menuDto.getApplicationId())
                    .orElseThrow(() -> {
                        logger.warn("Application not found with ID: {}", menuDto.getApplicationId());
                        return IgrpResponseStatusException.of(
                                HttpStatus.NOT_FOUND,
                                "Application not found",
                                "Application not found with id: " + menuDto.getApplicationId());
                    }));
            }

        var savedMenuEntry = menuEntryRepository.save(menuEntry);
        logger.info("""
                    Menu updated: id={}, name={}, type={}
                    """,
                savedMenuEntry.getId(),
                savedMenuEntry.getName(),
                savedMenuEntry.getType());

        return ResponseEntity.ok(menuEntryMapper.toDTO(savedMenuEntry));
    }

}