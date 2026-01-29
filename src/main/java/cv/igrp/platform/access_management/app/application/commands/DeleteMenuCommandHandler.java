package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Component
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<String>> {

   private final MenuEntryEntityRepository menuEntryRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final Logger logger = LoggerFactory.getLogger(DeleteMenuCommandHandler.class);

   /**
    * Constructs a new {@code DeleteMenuCommandHandler} with the required dependencies.
    *
    * @param menuEntryRepository the repository used to access and update {@link MenuEntryEntity} records
    * @param applicationRepository the repository used to access and update {@link ApplicationEntity} records
    */
   public DeleteMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository,  ApplicationEntityRepository applicationRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
   }

   /**
    * Handles the {@link DeleteMenuCommand} by performing a soft delete on the specified menu entry.
    * <p>
    * The menu entry is not physically deleted from the database. Instead, its status is updated
    * to {@link Status#DELETED}, allowing it to be ignored or filtered out in application logic.
    * </p>
    *
    * @param command the command containing the ID of the menu to delete
    * @return {@link ResponseEntity} with HTTP 204 No Content if deletion is successful
    * @throws IgrpResponseStatusException if the menu entry is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(DeleteMenuCommand command) {

      var appCode = command.getApplicationCode();

      var application = applicationRepository.findByCodeAndStatusNot(appCode, Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Application with code {} not found", appCode);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Application not found", "Application not found with code: " + appCode);
              });

      MenuEntryEntity menuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getMenuCode(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Menu entry with code {} not found", command.getMenuCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with code: " + command.getMenuCode());
              });

      deleteChildMenus(menuEntry);

      menuEntry.setCode(menuEntry.getCode() + "-" + UUID.randomUUID());
      menuEntry.setStatus(Status.DELETED);

      var deletedMenuEntry = menuEntryRepository.save(menuEntry);
      logger.info("""
                    Menu with code={}, name={}, type={} has been marked as deleted
                    """,
              deletedMenuEntry.getCode(),
              deletedMenuEntry.getName(),
              deletedMenuEntry.getType());

      return ResponseEntity.noContent().build();
   }

   private void deleteChildMenus(MenuEntryEntity menuEntry) {

       if(menuEntry == null) return;

       var children = menuEntryRepository.findByParentId(menuEntry);

       if(children.isEmpty()) return;

       for (var child :  children) {

           if(child.getStatus() == Status.DELETED) continue;

           deleteChildMenus(child);

           child.setCode(menuEntry.getCode() + "-" + UUID.randomUUID());
           child.setStatus(Status.DELETED);

           menuEntryRepository.save(child);

       }

   }

}