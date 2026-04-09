package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import cv.igrp.framework.core.domain.IgrpEnum;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import lombok.Getter;
import org.springframework.data.domain.Pageable;

@Getter
public class GetSessionsByRoleQuery implements Query {
    private final String roleCode;
    private final String departmentCode;
    private final SessionStatus status;
    private final Pageable pageable;
    
    public GetSessionsByRoleQuery(String roleCode, String departmentCode, SessionStatus status, Pageable pageable) {
        this.roleCode = roleCode;
        this.departmentCode = departmentCode;
        this.status = status;
        this.pageable = pageable;
    }
}
