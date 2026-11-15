package cv.igrp.platform.access_management.permissions.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionSummaryDTO;
import cv.igrp.platform.access_management.permissions.domain.service.PermissionNames;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class UpdatePermissionNameCommandHandler
        implements CommandHandler<UpdatePermissionNameCommand, ResponseEntity<PermissionSummaryDTO>> {

    private final PermissionNames names;

    public UpdatePermissionNameCommandHandler(PermissionNames names) {
        this.names = names;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<PermissionSummaryDTO> handle(UpdatePermissionNameCommand command) {
        return ResponseEntity.ok(names.update(command.key(), command.update()));
    }
}
