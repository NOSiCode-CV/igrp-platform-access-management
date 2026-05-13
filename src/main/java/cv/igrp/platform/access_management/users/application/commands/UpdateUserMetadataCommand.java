package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserMetadataCommand implements Command {

    @NotNull(message = "The field <id> is required")
    private String id;

    @NotNull(message = "The field <metadata> is required")
    private Map<String, Object> metadata;
}
