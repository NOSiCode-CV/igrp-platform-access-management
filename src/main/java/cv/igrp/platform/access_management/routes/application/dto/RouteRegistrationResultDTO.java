package cv.igrp.platform.access_management.routes.application.dto;

/**
 * {@code POST /api/routes/register} response. {@code deleted} and
 * {@code renamed} stay 0 until version-gated delete and rename (#11).
 */
public record RouteRegistrationResultDTO(int created, int unchanged, int deleted, int renamed) {
}
