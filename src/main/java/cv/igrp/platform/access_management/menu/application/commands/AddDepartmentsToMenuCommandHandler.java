package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

import java.util.List;

/**
 * Command handler responsible for adding departments to a specific menu entry.
 *
 * <p>
 * This handler:
 * <ul>
 *     <li>Fetches the menu entry by its code, ensuring it is not marked as {@link cv.igrp.platform.access_management.shared.application.constants.Status#DELETED}</li>
 *     <li>Iterates over the list of department IDs to add</li>
 *     <li>If the department exists and is not yet associated with the menu entry, it is added</li>
 *     <li>Saves the updated menu entry</li>
 * </ul>
 * The result is the updated {@link MenuEntryDTO} reflecting the newly associated departments.
 *
 * @see AddDepartmentsToMenuCommand
 * @see MenuEntryEntity
 * @see DepartmentEntity
 * @see MenuEntryEntityRepository
 * @see DepartmentEntityRepository
 * @see MenuEntryMapper
 * @see MenuEntryDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class AddDepartmentsToMenuCommandHandler implements CommandHandler<AddDepartmentsToMenuCommand, ResponseEntity<MenuEntryDTO>> {


   private final MenuEntryEntityRepository menuEntryRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final MenuEntryMapper menuEntryMapper;

    /**
     * Constructs a new instance of {@code AddDepartmentsToMenuCommandHandler} with the necessary dependencies.
     *
     * @param menuEntryRepository the repository used to retrieve and persist menu entry entities
     * @param departmentRepository the repository used to retrieve department entities
     * @param menuEntryMapper     the mapper used to convert between menu entry entities and DTO
     */
    public AddDepartmentsToMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, DepartmentEntityRepository departmentRepository, MenuEntryMapper menuEntryMapper) {
        // Initialize repositories and mapper here
        this.menuEntryRepository = menuEntryRepository;
        this.departmentRepository = departmentRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

   /**
    * Handles the {@link AddDepartmentsToMenuCommand} by adding the specified departments to the corresponding {@link MenuEntryEntity}.
    * <p>
    *     It validates the existence of the menu entry and each department before adding them to the menu entry.
    *     </p>
    *     @param command the command containing the menu code and list of department IDs to add
    *     @return {@link ResponseEntity} with status 200 OK and the updated {@link MenuEntryDTO}
    *     @throws cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException
    *     Exception if the menu or any department is not found
    */
   @IgrpCommandHandler
   public ResponseEntity<MenuEntryDTO> handle(AddDepartmentsToMenuCommand command) {

      List<String> departmentIds = command.getAddDepartmentsToMenuRequest();
        var menuEntryOpt = menuEntryRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED);
        if (menuEntryOpt.isEmpty()) {
            log.warn("Menu not found with code: {}", command.getCode());
            throw cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.of(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Menu not found",
                    "Menu not found with code: " + command.getCode());
        }
        var menuEntry = menuEntryOpt.get();
        for (String deptId : departmentIds) {
            var departmentOpt = departmentRepository.findByCodeAndStatusNot(deptId, DepartmentStatus.DELETED);
            if (departmentOpt.isEmpty()) {
                log.warn("Department not found with ID: {}", deptId);
                throw cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.of(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Department not found",
                        "Department not found with ID: " + deptId);
            }
            var department = departmentOpt.get();
            if (!menuEntry.getDepartments().contains(department)) {
                menuEntry.getDepartments().add(department);
                log.info("Added department <{}> to menu <{}>", deptId, command.getCode());
            } else {
                log.info("Department <{}> is already associated with menu <{}>", deptId, command.getCode());
            }
        }
        var updatedMenuEntry = menuEntryRepository.save(menuEntry);
        var response = menuEntryMapper.toDTO(updatedMenuEntry);
        return ResponseEntity.ok(response);
   }

}