package cv.igrp.platform.access_management.menu_entry.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.commands.commands.DeleteMenuCommand;



@Service
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<String>> {

   public DeleteMenuCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteMenuCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}