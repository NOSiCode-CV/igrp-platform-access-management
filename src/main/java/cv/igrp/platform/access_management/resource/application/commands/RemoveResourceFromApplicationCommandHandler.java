package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class RemoveResourceFromApplicationCommandHandler implements CommandHandler<RemoveResourceFromApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationEntityRepository;
    private final ResourceEntityRepository resourceEntityRepository;

    public RemoveResourceFromApplicationCommandHandler(ApplicationEntityRepository applicationEntityRepository, ResourceEntityRepository resourceEntityRepository) {
        this.applicationEntityRepository = applicationEntityRepository;
        this.resourceEntityRepository = resourceEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(RemoveResourceFromApplicationCommand command) {

        var resource = resourceEntityRepository.findByNameNotDeleted(command.getName());

        var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

        application.getResources().remove(resource);
        resource.getApplications().remove(application);

        applicationEntityRepository.save(application);
        resourceEntityRepository.save(resource);

        return ResponseEntity.noContent().build();
    }

}