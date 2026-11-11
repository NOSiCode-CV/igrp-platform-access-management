package cv.igrp.platform.access_management.routes.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationResultDTO;
import cv.igrp.platform.access_management.routes.domain.service.RouteRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class RegisterRoutesCommandHandler
        implements CommandHandler<RegisterRoutesCommand, ResponseEntity<RouteRegistrationResultDTO>> {

    private final RouteRegistrationService registrations;

    public RegisterRoutesCommandHandler(RouteRegistrationService registrations) {
        this.registrations = registrations;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<RouteRegistrationResultDTO> handle(RegisterRoutesCommand command) {
        return ResponseEntity.ok(registrations.register(command.registration()));
    }
}
