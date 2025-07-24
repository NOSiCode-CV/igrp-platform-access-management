package cv.igrp.platform.access_management.authorization.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component
public class BatchCheckAuthorizationCommandHandler implements CommandHandler<BatchCheckAuthorizationCommand, ResponseEntity<List<PermissionCheckResponseDTO>>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(BatchCheckAuthorizationCommandHandler.class);

   private final AuthenticationHelper authenticationHelper;
   private final SingleCheckAuthorizationHandler singleCheckAuthorizationHandler;

   public BatchCheckAuthorizationCommandHandler(AuthenticationHelper authenticationHelper, SingleCheckAuthorizationHandler singleCheckAuthorizationHandler) {
       this.authenticationHelper = authenticationHelper;
       this.singleCheckAuthorizationHandler = singleCheckAuthorizationHandler;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<PermissionCheckResponseDTO>> handle(BatchCheckAuthorizationCommand command) {

      final List<PermissionCheckResponseDTO> permissionCheckResponses = new ArrayList<>();

      var username = authenticationHelper.getPreferredUsername();

      for (var dto : command.getPermissioncheckrequest()) {
         var action = dto.getAction();
         var resource = dto.getResource();

         permissionCheckResponses.add(
            this.singleCheckAuthorizationHandler.checkAuthorization(username,action,resource)
         );
      }
      return ResponseEntity.ok(permissionCheckResponses);
   }

}