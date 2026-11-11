package cv.igrp.platform.access_management.active_role.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class GetActiveRoleQueryHandler implements QueryHandler<GetActiveRoleQuery, ResponseEntity<ActiveRoleDTO>> {

    private final ActiveRoleService activeRoles;

    public GetActiveRoleQueryHandler(ActiveRoleService activeRoles) {
        this.activeRoles = activeRoles;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<ActiveRoleDTO> handle(GetActiveRoleQuery query) {
        return ResponseEntity.ok(activeRoles.current(query.applicationCode()));
    }
}
