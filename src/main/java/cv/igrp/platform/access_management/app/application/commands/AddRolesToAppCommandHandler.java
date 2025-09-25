package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class AddRolesToAppCommandHandler implements CommandHandler<AddRolesToAppCommand, ResponseEntity<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddRolesToAppCommandHandler.class);

    public AddRolesToAppCommandHandler() {

    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(AddRolesToAppCommand command) {
        // TODO: Implement the command handling logic here
        return null;
    }

}