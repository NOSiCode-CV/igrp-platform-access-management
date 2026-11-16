package cv.igrp.platform.access_management.page_access.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/authorize/page} (v3-26, #34): may the caller open this page?
 *
 * @param applicationCode the frontend's application
 * @param path            the request path as the frontend sees it, without its base path
 *                        (a query or fragment is ignored)
 */
public record PageAccessRequestDTO(
        @NotBlank(message = "The field <applicationCode> is required")
        @Size(max = 255, message = "The field length <applicationCode> cannot be more than 255 characters")
        String applicationCode,
        @NotBlank(message = "The field <path> is required")
        @Size(max = 2048, message = "The field length <path> cannot be more than 2048 characters")
        String path) {
}
