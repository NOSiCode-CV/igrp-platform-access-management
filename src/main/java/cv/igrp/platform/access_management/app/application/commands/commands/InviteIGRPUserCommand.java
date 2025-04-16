package cv.igrp.platform.access_management.app.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.app.application.dto.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteIGRPUserCommand implements Command {
    @NotNull private Integer departmentId;
    private IGRPUserDTO userDTO;
}