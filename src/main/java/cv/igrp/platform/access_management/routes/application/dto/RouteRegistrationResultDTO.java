package cv.igrp.platform.access_management.routes.application.dto;

/**
 * {@code POST /api/routes/register} response.
 *
 * @param created   keys created, or deleted keys declared again (they come back with no assignments)
 * @param unchanged keys that already existed
 * @param deleted   the service's keys pending deletion after this registration: the newest version no
 *                  longer declares them, and they are deleted after the grace period (#11). 0 for a
 *                  registration from an older version, which never schedules deletions
 * @param renamed   keys rewritten by a rename of the service by the same client (#11)
 */
public record RouteRegistrationResultDTO(int created, int unchanged, int deleted, int renamed) {
}
