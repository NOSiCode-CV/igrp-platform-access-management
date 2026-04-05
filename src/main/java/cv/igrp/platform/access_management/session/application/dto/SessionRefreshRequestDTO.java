package cv.igrp.platform.access_management.session.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class SessionRefreshRequestDTO {

    @Min(value = 60, message = "Extension time cannot be less than 60 seconds")
    @Max(value = 7200, message = "Extension time cannot be more than 7200 seconds (2 hours)")
    private Integer extensionSeconds = 1800; // Default 30 minutes
}
