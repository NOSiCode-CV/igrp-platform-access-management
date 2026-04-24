package cv.igrp.platform.access_management.shared.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserMetadataRequestDTO {

    @NotNull(message = "metadata is required")
    private Map<String, Object> metadata;
}
