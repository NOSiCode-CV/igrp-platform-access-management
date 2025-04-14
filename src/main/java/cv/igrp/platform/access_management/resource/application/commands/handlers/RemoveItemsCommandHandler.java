package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.RemoveItemsCommand;



@Service
public class RemoveItemsCommandHandler implements CommandHandler<RemoveItemsCommand, ResponseEntity<ResourceDTO>> {

   public RemoveItemsCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(RemoveItemsCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}