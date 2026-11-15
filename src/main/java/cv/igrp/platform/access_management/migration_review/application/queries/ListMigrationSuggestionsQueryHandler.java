package cv.igrp.platform.access_management.migration_review.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.migration_review.application.dto.MigrationSuggestionDTO;
import cv.igrp.platform.access_management.migration_review.domain.service.MigrationSuggestions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListMigrationSuggestionsQueryHandler
        implements QueryHandler<ListMigrationSuggestionsQuery, ResponseEntity<List<MigrationSuggestionDTO>>> {

    private final MigrationSuggestions suggestions;

    public ListMigrationSuggestionsQueryHandler(MigrationSuggestions suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<List<MigrationSuggestionDTO>> handle(ListMigrationSuggestionsQuery query) {
        return ResponseEntity.ok(suggestions.list(query.applicationCode(), query.status()));
    }
}
