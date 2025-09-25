package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class RemoveDepartmentFromApplicationCommandHandler implements CommandHandler<RemoveDepartmentFromApplicationCommand, ResponseEntity<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveDepartmentFromApplicationCommandHandler.class);

    public RemoveDepartmentFromApplicationCommandHandler() {

    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(RemoveDepartmentFromApplicationCommand command) {
        // TODO: Implement the command handling logic here
        return null;
    }

}