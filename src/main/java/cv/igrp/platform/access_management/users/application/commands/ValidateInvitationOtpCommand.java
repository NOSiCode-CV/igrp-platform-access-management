package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateInvitationOtpCommand implements Command {

    @NotNull(message = "The field <otpId> is required")
    private Long otpId;

    @NotBlank(message = "The field <otpCode> is required")
    private String otpCode;

}
