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

    @NotBlank(message = "{validation.token.required}")
    private String token;

    @NotBlank(message = "{validation.otpCode.required}")
    private String otpCode;

}
