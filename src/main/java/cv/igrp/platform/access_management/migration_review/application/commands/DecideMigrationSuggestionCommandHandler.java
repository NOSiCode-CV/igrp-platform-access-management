package cv.igrp.platform.access_management.migration_review.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.migration_review.application.dto.MigrationSuggestionDTO;
import cv.igrp.platform.access_management.migration_review.domain.service.MigrationSuggestions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DecideMigrationSuggestionCommandHandler
        implements CommandHandler<DecideMigrationSuggestionCommand, ResponseEntity<MigrationSuggestionDTO>> {

    private final MigrationSuggestions suggestions;

    public DecideMigrationSuggestionCommandHandler(MigrationSuggestions suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<MigrationSuggestionDTO> handle(DecideMigrationSuggestionCommand command) {
        return ResponseEntity.ok(command.confirm() ? suggestions.confirm(command.id()) : suggestions.dismiss(command.id()));
    }
}
