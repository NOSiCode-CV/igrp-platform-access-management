package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class KillSessionsByRoleCommand implements Command {
    private final String roleCode;
    private final String departmentCode;
    private final String reason;
    private final String killedBy;
    
    public KillSessionsByRoleCommand(String roleCode, String departmentCode, String reason, String killedBy) {
        this.roleCode = roleCode;
        this.departmentCode = departmentCode;
        this.reason = reason;
        this.killedBy = killedBy;
    }
}
