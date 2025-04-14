package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.CreateResourceCommand;



@Service
public class CreateResourceCommandHandler implements CommandHandler<CreateResourceCommand, ResponseEntity<ResourceDTO>> {

   public CreateResourceCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(CreateResourceCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}