package cv.igrp.platform.access_management.authorization.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CheckAuthorizationCommandHandler implements CommandHandler<CheckAuthorizationCommand, ResponseEntity<PermissionCheckResponseDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(CheckAuthorizationCommandHandler.class);

    private final AuthenticationHelper authenticationHelper;
    private final SingleCheckAuthorizationHandler singleCheckAuthorizationHandler;

    public CheckAuthorizationCommandHandler(AuthenticationHelper authenticationHelper, SingleCheckAuthorizationHandler singleCheckAuthorizationHandler) {
        this.authenticationHelper = authenticationHelper;
        this.singleCheckAuthorizationHandler = singleCheckAuthorizationHandler;
    }

   @IgrpCommandHandler
   public ResponseEntity<PermissionCheckResponseDTO> handle(CheckAuthorizationCommand command) {
       var action = command.getPermissioncheckrequest().getAction();
       var resource =command.getPermissioncheckrequest().getResource();
       var username = authenticationHelper.getSub();

       return ResponseEntity.status(HttpStatus.OK).body(
               this.singleCheckAuthorizationHandler.checkAuthorization(username, action, resource)
       );
   }


}