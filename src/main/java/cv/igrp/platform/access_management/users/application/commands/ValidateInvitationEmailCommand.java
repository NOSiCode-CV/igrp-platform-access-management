package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateInvitationEmailCommand implements Command {

    @NotBlank(message = "The field <token> is required")
    private String token;

    @NotBlank(message = "The field <email> is required")
    private String email;

}
