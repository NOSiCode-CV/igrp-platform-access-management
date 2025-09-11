package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<String>> {

   private final MenuEntryEntityRepository menuEntryRepository;
   private final Logger logger = LoggerFactory.getLogger(DeleteMenuCommandHandler.class);

   /**
    * Constructs a new {@code DeleteMenuCommandHandler} with the required dependencies.
    *
    * @param menuEntryRepository the repository used to access and update {@link MenuEntryEntity} records
    */
   public DeleteMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository) {
      this.menuEntryRepository = menuEntryRepository;
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
   public ResponseEntity<String> handle(DeleteMenuCommand command) {

      MenuEntryEntity menuEntry = menuEntryRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Menu entry with code {} not found", command.getCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with code: " + command.getCode());
              });

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

}