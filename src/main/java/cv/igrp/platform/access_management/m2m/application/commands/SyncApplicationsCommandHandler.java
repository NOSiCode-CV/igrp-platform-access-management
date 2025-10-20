package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.m2m.domain.service.ApplicationSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command handler responsible for synchronizing applications.
 *
 * <p>
 * This handler receives a {@link SyncApplicationsCommand}, logs the synchronization action,
 * and delegates the synchronization process to the {@link ApplicationSyncService}.
 * </p>
 *
 * @see SyncApplicationsCommand
 * @see ApplicationSyncService
 */
@Component
public class SyncApplicationsCommandHandler implements CommandHandler<SyncApplicationsCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncApplicationsCommandHandler.class);

   private final ApplicationSyncService applicationSyncService;

   /**
    * Constructs the handler with the required dependencies.
    *
    * @param applicationSyncService the service used to synchronize applications
    */
   public SyncApplicationsCommandHandler(ApplicationSyncService applicationSyncService) {
      this.applicationSyncService = applicationSyncService;
   }

   /**
    * Handles the synchronization command.
    *
    * @param command the command containing the application data to synchronize
    * @return a response entity indicating the result of the synchronization
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(SyncApplicationsCommand command) {
      LOGGER.info("Synchronizing application: {}", command.getApplicationdto().getName());
      applicationSyncService.synchronizeApplication(command.getApplicationdto());
      return ResponseEntity.noContent().build();
   }

}