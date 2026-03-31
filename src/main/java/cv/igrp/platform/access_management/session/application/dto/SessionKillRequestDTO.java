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
public class SessionKillRequestDTO {

    @NotBlank(message = "Reason is mandatory")
    @Size(max = 255, message = "Reason cannot be more than 255 characters")
    private String reason;

    @Size(max = 100, message = "Killed by cannot be more than 100 characters")
    private String killedBy;
}
