package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for processing the {@link AddRolesToMenuCommand},
 * which adds a list of permissions to a given menu entry.
 * <p>
 * This handler ensures the target menu entry exists and is active (not deleted),
 * filters out any permissions marked as deleted, and attaches valid permissions
 * to the menu entry. The updated menu entry is then persisted, and the added permissions are returned as DTOs.
 * @see AddRolesToMenuCommand
 * @see PermissionEntity
 * @see PermissionDTO
 * @see PermissionEntityRepository
 * @see MenuEntryEntity
 * @see MenuEntryEntityRepository
 * @see PermissionMapper
 * @see IgrpResponseStatusException
 * @see Status
 */
@Slf4j
@Component
public class AddRolesToMenuCommandHandler implements CommandHandler<AddRolesToMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private final RoleEntityRepository roleRepository;
   private final MenuEntryEntityRepository menuEntryRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final MenuEntryMapper menuEntryMapper;

   /**
    * Constructs the handler with necessary repositories and mappers.
    *
    * @param roleRepository          repository used to retrieve roles by their names
    * @param menuEntryRepository       repository used to retrieve and save menu entries
    * @param applicationRepository repository used to retrieve applications by their codes
    * @param departmentRepository repository used to fetch department
    * @param menuEntryMapper     mapper for converting {@link MenuEntryEntity} entities to {@link MenuEntryDTO},
    */
   public AddRolesToMenuCommandHandler(
           RoleEntityRepository roleRepository,
           MenuEntryEntityRepository menuEntryRepository,
           ApplicationEntityRepository applicationRepository,
           MenuEntryMapper menuEntryMapper,
           DepartmentEntityRepository departmentRepository
   ) {
      this.roleRepository = roleRepository;
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
      this.departmentRepository = departmentRepository;
      this.menuEntryMapper = menuEntryMapper;
   }

   /**
    * Handles the addition of roles to a specific menu entry.
    * <ul>
    *     <li>Fetches the list of roles by ID, ignoring any with DELETED status.</li>
    *     <li>Validates the existence of the target menu entry and ensures it's not deleted.</li>
    *     <li>Adds the valid roles to the menu entry and persists the updated menu entry.</li>
    *     <li>Returns a list of {@link RoleDTO} objects representing the added roles.</li>
    * </ul>
    *
    * @param command the command containing the menu entry code and list of role names to add
    * @return a {@link ResponseEntity} containing the added roles
    * @throws IgrpResponseStatusException if the menu entry is not found or is marked as deleted
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<MenuEntryDTO> handle(AddRolesToMenuCommand command) {

      String departmentCode = command.getDepartmentCode();
      List<String> roleIdList = command.getAddRolesToMenuRequest();
      log.info("Add Roles: {} for menu entry: {}.", roleIdList, command.getMenuCode());

      var department = departmentRepository.findByCodeAndStatusNotDeleted(departmentCode);

      List<RoleEntity> roleList = roleRepository.findAllByDepartmentAndCodeInNotDeleted(department, roleIdList, Status.DELETED);

      if (roleList.isEmpty()) {
         log.warn("No role available from given set: {} ", command.getAddRolesToMenuRequest().stream().toList());
         throw IgrpResponseStatusException.ofWithDetails(IgrpErrorCode.IGRP_AUTH_ROLES_NOT_FOUND, roleIdList, roleIdList);
      }

      ApplicationEntity application = applicationRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

      MenuEntryEntity foundMenu = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getMenuCode(), Status.DELETED)
              .orElseThrow(() -> {
                 log.warn("Menu Entry with code: {} not found.", command.getMenuCode());
                 return IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_MENU_ENTRY_NOT_FOUND, command.getMenuCode());
              });

      foundMenu.getRoles().addAll(roleList);
      MenuEntryEntity savedMenu = menuEntryRepository.save(foundMenu);

      Set<Integer> addedRoleIds = roleList.stream()
              .map(RoleEntity::getId)
              .collect(Collectors.toSet());

      MenuEntryDTO response = menuEntryMapper.toDTO(savedMenu);
      log.info("Permissions: {} for menu entry: {} added successfully.", addedRoleIds, command.getMenuCode());
      return new ResponseEntity<>(response, HttpStatus.OK);

   }

}