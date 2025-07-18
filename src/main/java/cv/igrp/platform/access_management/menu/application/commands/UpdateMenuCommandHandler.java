package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.application.domain.service.MenuEntryValidator;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@Component
public class UpdateMenuCommandHandler implements CommandHandler<UpdateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private static final Logger logger = LoggerFactory.getLogger(UpdateMenuCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final MenuEntryMapper menuEntryMapper;
   private final ApplicationEntityRepository applicationRepository;
   private final PermissionEntityRepository permissionEntityRepository;

   /**
    * Constructs an {@code UpdateMenuCommandHandler} with required dependencies.
    *
    * @param menuEntryRepository the repository used to access and persist {@link MenuEntryEntity} entities
    * @param menuEntryMapper the mapper used to convert between {@link MenuEntryEntity} and {@link MenuEntryDTO}
    * @param applicationRepository the repository used to validate and retrieve associated {@link ApplicationEntity} entities
    */
   public UpdateMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationEntityRepository applicationRepository, PermissionEntityRepository permissionEntityRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
      this.menuEntryMapper = menuEntryMapper;
      this.permissionEntityRepository = permissionEntityRepository;
   }

   /**
    * Handles the {@link UpdateMenuCommand} by updating the corresponding {@link MenuEntryEntity}
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

      MenuEntryEntity menuEntry = menuEntryRepository.findById(command.getId())
              .orElseThrow(() -> {
                 logger.warn("Menu not found with ID: {}", command.getId());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Menu not found",
                         "Menu not found with id: " + command.getId());
              });

      MenuEntryDTO menuDto = command.getMenuentrydto();

      MenuEntryValidator.validateRequiredFields(menuDto);

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

      if (menuDto.getPermissions() != null) {

         List<String> newPermissions = menuDto.getPermissions();
         List<PermissionEntity> existingPermissions = permissionEntityRepository.findByMenuEntryId(menuEntry);

         List<String> existingPermissionNames = existingPermissions.stream()
                 .map(PermissionEntity::getName)
                 .toList();

         // Add new permissions
         for (String permission : newPermissions) {
            if (!existingPermissionNames.contains(permission)) {
               addPermissionToMenuEntry(permission, menuEntry);
            }
         }

         // Remove permissions that are no longer present
         for (PermissionEntity permissionEntity : existingPermissions) {
            if (!newPermissions.contains(permissionEntity.getName())) {
               permissionEntityRepository.delete(permissionEntity);
            }
         }
      }

      logger.info("""
                    Menu updated: id={}, name={}, type={}
                    """,
              savedMenuEntry.getId(),
              savedMenuEntry.getName(),
              savedMenuEntry.getType());

      return ResponseEntity.ok(menuEntryMapper.toDTO(savedMenuEntry));
   }

   private void addPermissionToMenuEntry(String permissionName, MenuEntryEntity menuEntry) {
      PermissionEntity newPermission = new PermissionEntity();
      newPermission.setName(permissionName);
      newPermission.setMenuEntryId(menuEntry);
      permissionEntityRepository.save(newPermission);
   }


}