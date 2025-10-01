package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareResourceToAnotherApplicationCommand implements Command {

    @NotBlank(message = "The field <name> is required")
    private String name;
    @NotBlank(message = "The field <applicationCode> is required")
    private String applicationCode;

}