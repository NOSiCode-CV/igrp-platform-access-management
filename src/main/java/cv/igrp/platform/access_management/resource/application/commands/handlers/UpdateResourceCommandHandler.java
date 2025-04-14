package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.UpdateResourceCommand;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;

@Service
public class UpdateResourceCommandHandler implements CommandHandler<UpdateResourceCommand, ResponseEntity<ResourceDTO>> {

   public UpdateResourceCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(UpdateResourceCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}