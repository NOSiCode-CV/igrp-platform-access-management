package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.PostDepartmentCommand;



@Service
public class PostDepartmentCommandHandler implements CommandHandler<PostDepartmentCommand, ResponseEntity<String>> {

   public PostDepartmentCommandHandler() {

   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(PostDepartmentCommand command) {
      // TODO: Implement the command handling logic here
      return null;
   }

}