package cv.igrp.platform.access_management.menu_entry.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.commands.commands.CreateMenuCommand;



@Service
public class CreateMenuCommandHandler implements CommandHandler<CreateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   public CreateMenuCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<MenuEntryDTO> handle(CreateMenuCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}