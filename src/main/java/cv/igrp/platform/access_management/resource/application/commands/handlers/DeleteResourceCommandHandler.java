package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.DeleteResourceCommand;



@Service
public class DeleteResourceCommandHandler implements CommandHandler<DeleteResourceCommand, ResponseEntity<String>> {

   public DeleteResourceCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteResourceCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}