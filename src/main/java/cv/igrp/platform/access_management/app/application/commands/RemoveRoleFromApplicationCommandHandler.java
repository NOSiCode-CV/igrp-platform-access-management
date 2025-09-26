package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class RemoveRoleFromApplicationCommandHandler implements CommandHandler<RemoveRoleFromApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleEntityRepository;

    public RemoveRoleFromApplicationCommandHandler(ApplicationEntityRepository applicationRepository, RoleEntityRepository roleEntityRepository) {

        this.applicationRepository = applicationRepository;
        this.roleEntityRepository = roleEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(RemoveRoleFromApplicationCommand command) {

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());

        for (var roleName : command.getCodelistrequestdto().getCodes()) {

            var role = roleEntityRepository.findByNameAndStatusNotDeleted(roleName);

            application.getRoles().remove(role);
        }

        applicationRepository.save(application);

        return ResponseEntity.noContent().build();
    }
}