-- V15_1: Widen t_application.slug from VARCHAR(50) to VARCHAR(255) to match the
-- ApplicationDTO's @Size(max = 255) validation. Before this change, the DTO
-- accepted up to 255 chars but the column silently truncated at 50 (or the
-- write failed with a data-truncation exception on strict SQL modes).

ALTER TABLE t_application
    ALTER COLUMN slug TYPE VARCHAR(255);
