package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

import java.util.List;

/**
 * Command handler responsible for removing departments from a specific menu entry.
 * <p>
 * This handler is intended to process the {@link RemoveDepartmentsFromMenuCommand} command,
 * which encapsulates the necessary information to identify the menu entry and the departments
 * to be removed.
 * </p>
 *
 * @see RemoveDepartmentsFromMenuCommand
 * @see MenuEntryDTO
 */
@Slf4j
@Component
public class RemoveDepartmentsFromMenuCommandHandler implements CommandHandler<RemoveDepartmentsFromMenuCommand, ResponseEntity<MenuEntryDTO>> {

    private final MenuEntryEntityRepository menuEntryRepository;
    private final DepartmentEntityRepository departmentEntityRepository;
    private final MenuEntryMapper menuEntryMapper;

    /**
     * Constructs a new instance of {@code RemoveDepartmentsFromMenuCommandHandler} with the necessary dependencies.
     *
     * @param menuEntryRepository        the repository used to retrieve and persist menu entry entities
     * @param departmentEntityRepository the repository used to retrieve and manage department entities
     * @param menuEntryMapper            the mapper used to convert between menu entry entities and DTO
     */
    public RemoveDepartmentsFromMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, DepartmentEntityRepository departmentEntityRepository, MenuEntryMapper menuEntryMapper) {
        this.menuEntryRepository = menuEntryRepository;
        this.departmentEntityRepository = departmentEntityRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    /**
     * Handles the {@link RemoveDepartmentsFromMenuCommand} by removing the specified departments
     * from the identified menu entry.
     * <p>
     * The method retrieves the menu entry using the provided identifier, removes the associations
     * with the specified departments, and persists the updated menu entry. The result is a
     * {@link MenuEntryDTO} reflecting the changes.
     * </p>
     *
     * @param command the command containing the menu entry identifier and the list of departments to remove
     * @return a {@link ResponseEntity} containing the updated {@link MenuEntryDTO}
     * @throws IgrpResponseStatusException if the menu entry or any of the specified departments are not found
     */
    @IgrpCommandHandler
    public ResponseEntity<MenuEntryDTO> handle(RemoveDepartmentsFromMenuCommand command) {

        List<String> departmentIds = command.getRemoveDepartmentsFromMenuRequest();
        var menuEntry = menuEntryRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Menu not found with code: {}", command.getCode());
                    return IgrpResponseStatusException.of(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "Menu not found",
                            "Menu not found with code: " + command.getCode());
                });
        departmentIds.forEach(departmentId -> {
            var department = departmentEntityRepository.findByCodeAndStatusNot(departmentId, DepartmentStatus.DELETED).orElseThrow(() -> {
                log.warn("Department not found with code: <{}>", departmentId);
                return IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND,
                        "Department not found",
                        "Department not found with id: " + departmentId);
            });
            if (menuEntry.getDepartments().contains(department)) {
                menuEntry.getDepartments().remove(department);
                log.info("Department with code: {} removed from menu entry with code: {}.", departmentId, command.getCode());
            } else {
                log.info("Department with code: {} not associated with menu entry with code: {}.", departmentId, command.getCode());
            }
        });
        var updatedMenuEntry = menuEntryRepository.save(menuEntry);
        log.info("Menu entry with code: {} updated successfully.", command.getCode());
        return ResponseEntity.ok(menuEntryMapper.toDTO(updatedMenuEntry));
    }

}