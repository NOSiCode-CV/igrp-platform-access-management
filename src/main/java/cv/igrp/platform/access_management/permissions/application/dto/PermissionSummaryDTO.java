package cv.igrp.platform.access_management.permissions.application.dto;

/**
 * A keyed permission as App Center's settings list shows it (v3-04, #12): by
 * display name, which is the key when code gave none.
 */
public record PermissionSummaryDTO(String key, String name, String description, String service,
                                   String applicationCode) {
}
