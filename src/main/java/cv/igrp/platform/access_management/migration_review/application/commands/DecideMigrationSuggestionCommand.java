package cv.igrp.platform.access_management.migration_review.application.commands;

import cv.igrp.framework.core.domain.Command;

/** Confirm (copy the assignments) or dismiss one suggestion. */
public record DecideMigrationSuggestionCommand(int id, boolean confirm) implements Command {
}
