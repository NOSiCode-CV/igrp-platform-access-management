package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

@Getter
public class KillSessionsByDepartmentCommand implements Command {
    private final String departmentCode;
    private final String reason;
    private final String killedBy;
    
    public KillSessionsByDepartmentCommand(String departmentCode, String reason, String killedBy) {
        this.departmentCode = departmentCode;
        this.reason = reason;
        this.killedBy = killedBy;
    }
}
