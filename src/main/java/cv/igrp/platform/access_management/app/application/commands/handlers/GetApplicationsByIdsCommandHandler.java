package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.GetApplicationsByIdsCommand;

import java.util.List;

@Service
public class GetApplicationsByIdsCommandHandler implements CommandHandler<GetApplicationsByIdsCommand, ResponseEntity<List<ApplicationDTO>>> {

   public GetApplicationsByIdsCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsByIdsCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}