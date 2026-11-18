package cv.igrp.platform.access_management.page_access.application.dto;

/**
 * {@code POST /api/manifests/register} response.
 *
 * @param created   pages registered for the first time, or deleted pages declared again
 * @param updated   pages whose path or keys the newest version changed
 * @param unchanged pages already registered as declared, or left as they were by an older version
 * @param deleted   pages the newest version no longer declares, now DELETED; 0 for an older version
 */
public record ManifestRegistrationResultDTO(int created, int updated, int unchanged, int deleted) {
}
