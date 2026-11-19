package cv.igrp.platform.access_management.page_access.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/manifests/register} body (v3-27, #35): every page manifest of
 * a frontend, sent at its startup with its service account.
 *
 * @param version the frontend's version; versions compare as route registration's do
 * @param pages   one entry per page manifest
 */
public record ManifestRegistrationRequestDTO(
        @NotBlank(message = "The field <version> is required")
        @Size(max = 64, message = "The field length <version> cannot be more than 64 characters")
        String version,
        @NotNull(message = "The field <pages> is required")
        @Size(max = 2000, message = "A registration cannot have more than 2000 pages")
        List<@Valid @NotNull PageManifestDTO> pages) {

    /**
     * One page manifest, as the file next to the page declares it.
     *
     * @param application the application code; it must be the calling client's
     * @param page        the page key, {@code app.<application>.<page>}
     * @param path        the page's Next.js App Router path, e.g. {@code /settings/users/[id]}
     * @param required    the permission keys the page needs; the page check requires them all
     * @param optional    keys an administrator may grant with the page, each with a label
     */
    public record PageManifestDTO(
            @NotBlank(message = "The field <application> is required") String application,
            @NotBlank(message = "The field <page> is required")
            @Size(max = 255, message = "The field length <page> cannot be more than 255 characters") String page,
            @NotBlank(message = "The field <path> is required")
            @Size(max = 512, message = "The field length <path> cannot be more than 512 characters") String path,
            List<@NotBlank String> required,
            List<@Valid @NotNull OptionalPermissionDTO> optional) {

        public List<String> requiredKeys() {
            return required == null ? List.of() : required;
        }

        public List<OptionalPermissionDTO> optionalKeys() {
            return optional == null ? List.of() : optional;
        }
    }

    /** A key an administrator may grant with the page, and how App Center labels it. */
    public record OptionalPermissionDTO(
            @NotBlank(message = "The field <key> is required") String key,
            @Size(max = 255, message = "The field length <label> cannot be more than 255 characters") String label) {
    }
}
