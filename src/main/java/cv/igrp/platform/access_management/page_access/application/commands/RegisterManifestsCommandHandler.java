package cv.igrp.platform.access_management.page_access.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationResultDTO;
import cv.igrp.platform.access_management.page_access.domain.service.PageManifestService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class RegisterManifestsCommandHandler
        implements CommandHandler<RegisterManifestsCommand, ResponseEntity<ManifestRegistrationResultDTO>> {

    private final PageManifestService manifests;

    public RegisterManifestsCommandHandler(PageManifestService manifests) {
        this.manifests = manifests;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<ManifestRegistrationResultDTO> handle(RegisterManifestsCommand command) {
        return ResponseEntity.ok(manifests.register(command.registration()));
    }
}
