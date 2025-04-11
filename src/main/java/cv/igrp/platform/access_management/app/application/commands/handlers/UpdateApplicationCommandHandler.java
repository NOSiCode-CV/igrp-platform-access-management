package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.UpdateApplicationCommand;



@Service
public class UpdateApplicationCommandHandler implements CommandHandler<UpdateApplicationCommand, ResponseEntity<ApplicationDTO>> {

   public UpdateApplicationCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<ApplicationDTO> handle(UpdateApplicationCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}