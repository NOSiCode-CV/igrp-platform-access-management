package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.DeleteApplicationCommand;



@Service
public class DeleteApplicationCommandHandler implements CommandHandler<DeleteApplicationCommand, ResponseEntity<String>> {

   public DeleteApplicationCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteApplicationCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}