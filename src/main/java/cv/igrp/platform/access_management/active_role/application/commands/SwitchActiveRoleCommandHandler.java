package cv.igrp.platform.access_management.active_role.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class SwitchActiveRoleCommandHandler
        implements CommandHandler<SwitchActiveRoleCommand, ResponseEntity<ActiveRoleDTO>> {

    private final ActiveRoleService activeRoles;

    public SwitchActiveRoleCommandHandler(ActiveRoleService activeRoles) {
        this.activeRoles = activeRoles;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<ActiveRoleDTO> handle(SwitchActiveRoleCommand command) {
        return ResponseEntity.ok(activeRoles.switchTo(command.applicationCode(),
                command.request().getRoleCode(), command.request().getDepartmentCode()));
    }
}
