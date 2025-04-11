package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.CreateApplicationCommand;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Service
public class CreateApplicationCommandHandler implements CommandHandler<CreateApplicationCommand, ResponseEntity<?>> {

   public CreateApplicationCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<?> handle(CreateApplicationCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}