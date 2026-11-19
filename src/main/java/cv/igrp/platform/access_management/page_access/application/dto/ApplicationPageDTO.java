package cv.igrp.platform.access_management.page_access.application.dto;

import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO.OptionalPermissionDTO;

import java.util.List;

/**
 * A registered page of an application (v3-27, #35), as App Center shows it to
 * grant it to a role.
 *
 * @param page     the page key
 * @param path     the page's App Router path
 * @param required the keys granting the page assigns
 * @param optional the keys an administrator may add, with their labels
 */
public record ApplicationPageDTO(String page, String path, List<String> required,
                                 List<OptionalPermissionDTO> optional) {
}
