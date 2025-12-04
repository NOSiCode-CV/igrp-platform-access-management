package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.m2m.domain.service.MenuEntrySyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Command handler responsible for synchronizing menus.
 *
 * <p>
 * This handler receives a {@link SyncApplicationMenusCommand}, logs the synchronization action,
 * and invokes the {@link MenuEntrySyncService} to perform the synchronization of the specific application's menus
 * </p>
 *
 * @see SyncApplicationMenusCommand
 * @see MenuEntrySyncService
 */
@Component
public class SyncApplicationMenusCommandHandler implements CommandHandler<SyncApplicationMenusCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncApplicationMenusCommandHandler.class);

   private final MenuEntrySyncService menuEntrySyncService;

   /**
    * Constructs the handler with the required dependencies.
    *
    * @param menuEntrySyncService the service used to synchronize application's menus
    */
   public SyncApplicationMenusCommandHandler(MenuEntrySyncService menuEntrySyncService) {
      this.menuEntrySyncService = menuEntrySyncService;
   }

   /**
    * Handles the synchronization of application's menus.
    *
    * @param command the command containing the application's menus to be synchronized
    * @return a response entity indicating the result of the synchronization
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(SyncApplicationMenusCommand command) {
      LOGGER.info("Synchronizing menus for application: {}", command.getCode());
      menuEntrySyncService.synchronizeMenuEntries(command.getCode(), command.getMenuentrydto());
      return ResponseEntity.noContent().build();
   }

}