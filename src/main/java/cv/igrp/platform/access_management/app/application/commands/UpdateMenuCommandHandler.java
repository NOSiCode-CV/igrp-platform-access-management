package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.domain.service.MenuEntryValidator;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class UpdateMenuCommandHandler implements CommandHandler<UpdateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private static final Logger logger = LoggerFactory.getLogger(UpdateMenuCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final MenuEntryMapper menuEntryMapper;
   private final ApplicationEntityRepository applicationRepository;

   /**
    * Constructs an {@code UpdateMenuCommandHandler} with required dependencies.
    *
    * @param menuEntryRepository the repository used to access and persist {@link MenuEntryEntity} entities
    * @param menuEntryMapper the mapper used to convert between {@link MenuEntryEntity} and {@link MenuEntryDTO}
    * @param applicationRepository the repository used to validate and retrieve associated {@link ApplicationEntity} entities
    */
   public UpdateMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationEntityRepository applicationRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
      this.menuEntryMapper = menuEntryMapper;
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
      // Find the menu entry by ID, application, and status not deleted
      ApplicationEntity app = applicationRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());
      // Find the menu entry by application ID, code, and status not deleted
      MenuEntryEntity menuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, command.getMenuCode(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Menu not found with code: {}", command.getMenuCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Menu not found",
                         "Menu not found with code: " + command.getMenuCode());
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

      if (menuDto.getParentCode() != null) {
         menuEntry.setParentId(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuDto.getParentCode(), Status.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Parent Menu not found with code: {}", menuDto.getParentCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Parent Menu Entry not found",
                            "Parent Menu Entry not found with code: " + menuDto.getParentCode());
                 }));
      }

      if (menuDto.getApplicationCode() != null){
         menuEntry.setApplicationId(applicationRepository.findByCodeAndStatusNot(menuDto.getApplicationCode(), Status.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Application not found with code: {}", menuDto.getApplicationCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Application not found",
                            "Application not found with code: " + menuDto.getApplicationCode());
                 }));
      }

      var savedMenuEntry = menuEntryRepository.save(menuEntry);

      logger.info("""
                    Menu updated: code={}, name={}, type={}
                    """,
              savedMenuEntry.getCode(),
              savedMenuEntry.getName(),
              savedMenuEntry.getType());

      return ResponseEntity.ok(menuEntryMapper.toDTO(savedMenuEntry));
   }

}