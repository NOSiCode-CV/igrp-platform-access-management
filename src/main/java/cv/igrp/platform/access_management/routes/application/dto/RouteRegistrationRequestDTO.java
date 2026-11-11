package cv.igrp.platform.access_management.routes.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code POST /api/routes/register} body (v3-02, spec "Route registration
 * contract"), sent by the 0.3 starter at startup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRegistrationRequestDTO {

    @NotBlank(message = "The field <service> is required")
    @Size(max = 120, message = "The field length <service> cannot be more than 120 characters")
    private String service;

    @NotBlank(message = "The field <version> is required")
    @Size(max = 64, message = "The field length <version> cannot be more than 64 characters")
    private String version;

    @NotNull(message = "The field <routes> is required")
    @Size(max = 5000, message = "A registration cannot have more than 5000 routes")
    @Valid
    private List<RegisteredRouteDTO> routes;

    /** One route: its handler mapping and the key the starter derived or read from {@code @Permission}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredRouteDTO {

        @NotBlank(message = "The field <method> is required")
        private String method;

        @NotBlank(message = "The field <path> is required")
        private String path;

        @NotBlank(message = "The field <key> is required")
        private String key;

        private boolean annotated;

        @Size(max = 255, message = "The field length <name> cannot be more than 255 characters")
        private String name;

        @Size(max = 255, message = "The field length <description> cannot be more than 255 characters")
        private String description;

        /** The legacy permission of the route's {@code @PreAuthorize}. Not sent yet; used by the migration review (#37). */
        private String legacyKey;
    }
}
