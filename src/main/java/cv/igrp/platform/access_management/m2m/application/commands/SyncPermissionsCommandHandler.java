package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.m2m.domain.service.PermissionSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command handler responsible for synchronizing permissions in a machine-to-machine context.
 * <p>
 * This handler processes the {@link SyncPermissionsCommand} by delegating the synchronization logic
 * to the {@link PermissionSyncService}. It logs the number of permissions being synchronized.
 * </p>
 *
 * @see SyncPermissionsCommand
 * @see PermissionSyncService
 */
@Component
public class SyncPermissionsCommandHandler implements CommandHandler<SyncPermissionsCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SyncPermissionsCommandHandler.class);

   private final PermissionSyncService permissionSyncService;

   /**
    * Constructs the handler with the required permission synchronization service.
    *
    * @param permissionSyncService the service responsible for synchronizing permissions
    */
   public SyncPermissionsCommandHandler(PermissionSyncService permissionSyncService) {
      this.permissionSyncService = permissionSyncService;
   }

   /**
    * Handles the synchronization of permissions.
    *
    * @param command the command containing the permissions to synchronize
    * @return a response entity indicating the result of the operation
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(SyncPermissionsCommand command) {
      LOGGER.info("Synchronizing {} permissions...", command.getPermissiondto().size());
      permissionSyncService.synchronizePermissions(command.getPermissiondto(), false);
      return ResponseEntity.noContent().build();
   }

}