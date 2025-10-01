package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ShareResourceToAnotherApplicationCommandHandler implements CommandHandler<ShareResourceToAnotherApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationEntityRepository;
    private final ResourceEntityRepository resourceEntityRepository;

    public ShareResourceToAnotherApplicationCommandHandler(ApplicationEntityRepository applicationEntityRepository, ResourceEntityRepository resourceEntityRepository) {
        this.applicationEntityRepository = applicationEntityRepository;
        this.resourceEntityRepository = resourceEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(ShareResourceToAnotherApplicationCommand command) {

        var resource = resourceEntityRepository.findByNameNotDeleted(command.getName());

        var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

        application.getResources().add(resource);

        applicationEntityRepository.save(application);

        return ResponseEntity.ok().build();
    }

}