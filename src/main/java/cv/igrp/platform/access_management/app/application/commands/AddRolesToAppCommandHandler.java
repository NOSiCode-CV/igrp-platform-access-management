package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AddRolesToAppCommandHandler implements CommandHandler<AddRolesToAppCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleEntityRepository;

    public AddRolesToAppCommandHandler(ApplicationEntityRepository applicationRepository, RoleEntityRepository roleEntityRepository) {
        this.applicationRepository = applicationRepository;
        this.roleEntityRepository = roleEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(AddRolesToAppCommand command) {

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());

        for (var roleName : command.getCodelistrequestdto().getCodes()) {

            var role = roleEntityRepository.findByNameAndStatusNotDeleted(roleName);

            application.getRoles().add(role);
        }

        applicationRepository.save(application);

        return ResponseEntity.ok().build();
    }

}