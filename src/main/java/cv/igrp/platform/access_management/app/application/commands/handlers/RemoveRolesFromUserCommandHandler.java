package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.RemoveRolesFromUserCommand;

import java.util.List;

@Service
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<Role>>> {

   public RemoveRolesFromUserCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<List<Role>> handle(RemoveRolesFromUserCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}