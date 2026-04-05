package cv.igrp.platform.access_management.session.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class SessionInitRequestDTO {

    @Size(max = 45, message = "Client IP cannot be more than 45 characters")
    private String clientIp;

    @Size(max = 128, message = "Device ID cannot be more than 128 characters")
    private String deviceId;

    @Size(max = 512, message = "User agent cannot be more than 512 characters")
    private String userAgent;
}
