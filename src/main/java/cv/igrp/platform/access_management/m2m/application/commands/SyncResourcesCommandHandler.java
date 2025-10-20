package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.m2m.domain.service.ResourceSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command handler responsible for synchronizing resources.
 *
 * <p>
 * This handler receives a {@link SyncResourcesCommand}, logs the synchronization action,
 * and invokes the {@link ResourceSyncService} to perform the synchronization of the specified resource.
 * </p>
 *
 * @see SyncResourcesCommand
 * @see ResourceSyncService
 */
@Component
public class SyncResourcesCommandHandler implements CommandHandler<SyncResourcesCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncResourcesCommandHandler.class);

   private final ResourceSyncService resourceSyncService;

   /**
    * Constructs the handler with the required dependencies.
    *
    * @param resourceSyncService the service used to synchronize resources
    */
   public SyncResourcesCommandHandler(ResourceSyncService resourceSyncService) {
      this.resourceSyncService = resourceSyncService;
   }

   /**
    * Handles the synchronization of resources.
    *
    * @param command the command containing the resource to be synchronized
    * @return a response entity indicating the result of the synchronization
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(SyncResourcesCommand command) {
      LOGGER.info("Synchronizing resource: {}", command.getResourcedto().getName());
      resourceSyncService.synchronizeResource(command.getResourcedto());
      return ResponseEntity.noContent().build();
   }

}