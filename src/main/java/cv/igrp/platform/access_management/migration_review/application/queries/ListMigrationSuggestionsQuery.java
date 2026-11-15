package cv.igrp.platform.access_management.migration_review.application.queries;

import cv.igrp.framework.core.domain.Query;

/** The suggestions, optionally of one application, with one status (PENDING by default). */
public record ListMigrationSuggestionsQuery(String applicationCode, String status) implements Query {
}
