package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.commands.RemovePermissionsCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for removing a list of permissions from a specific menu entry.
 * <p>
 * This handler:
 * <ul>
 *     <li>Fetches the menu entry by its code, ensuring it is not marked as {@link Status#DELETED}</li>
 *     <li>Iterates over the list of permission names to remove</li>
 *     <li>If the permission exists in the menu entry, it is removed and mapped to a {@link PermissionDTO}</li>
 *     <li>Saves the updated menu entry</li>
 * </ul>
 * The result is a list of {@link PermissionDTO}s that were successfully removed from the menu entry.
 *
 * @see RemovePermissionsFromMenuCommand
 * @see MenuEntryEntity
 * @see PermissionEntity
 * @see MenuEntryEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class RemovePermissionsFromMenuCommandHandler implements CommandHandler<RemovePermissionsFromMenuCommand, ResponseEntity<MenuEntryDTO>> {

    private final MenuEntryEntityRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;

    /**
     * Constructs a new instance of {@code RemovePermissionsFromMenuCommandHandler} with the necessary dependencies.
     *
     * @param menuEntryRepository the repository used to retrieve and persist menu entry entities
     * @param menuEntryMapper     the mapper used to convert between menu entry entities and DTO
     */
    public RemovePermissionsFromMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
        this.menuEntryRepository = menuEntryRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    /**
     * Handles the removal of roles from a menu entry.
     * <p>
     * For each role name provided in the {@link RemovePermissionsCommand}, the method checks whether
     * the role is currently associated with the menu entry. If so, it is removed from the menu entry and included
     * in the response.
     *
     * @param command the command containing the menu entry code and a list of role names to remove
     * @return a {@link ResponseEntity} with the list of removed roles as {@link MenuEntryDTO}s and HTTP status {@code 200 OK}
     * @throws IgrpResponseStatusException if the menu entry does not exist or is marked as {@link Status#DELETED}
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<MenuEntryDTO> handle(RemovePermissionsFromMenuCommand command) {

        log.info("Remove Roles with name: {} from menu entry with code: {}.", command.getRemovePermissionsFromMenuRequest().stream().toList(), command.getCode());

        MenuEntryEntity foundMenu = menuEntryRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Menu Entry with code: {} not found.", command.getCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Remove Role By Menu Entry code", "Menu Entry with code: " + command.getCode() + " not found."
                    );
                });

        for (String roleId : command.getRemovePermissionsFromMenuRequest()) {
            foundMenu.getRoles()
                    .stream()
                    .filter(role -> role.getName().equals(roleId))
                    .findFirst()
                    .ifPresent(role -> foundMenu.getRoles().remove(role));
        }
        log.info("Roles with IDs {} removed from Role with name: {} successfully.", command.getRemovePermissionsFromMenuRequest().stream().toList(), command.getCode());
        var response = menuEntryMapper.toDTO(menuEntryRepository.save(foundMenu));
        return new ResponseEntity<>(response, HttpStatus.OK);

    }

}