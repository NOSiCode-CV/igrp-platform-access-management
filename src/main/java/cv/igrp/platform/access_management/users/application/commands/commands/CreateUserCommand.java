package cv.igrp.platform.access_management.users.application.commands.commands;

import java.util.List;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserCommand implements Command{
    private String name;
    private String username;
    private String email;
}