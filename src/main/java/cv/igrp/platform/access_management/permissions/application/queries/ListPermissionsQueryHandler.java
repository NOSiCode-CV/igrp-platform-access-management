package cv.igrp.platform.access_management.permissions.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionSummaryDTO;
import cv.igrp.platform.access_management.permissions.domain.service.PermissionNames;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListPermissionsQueryHandler
        implements QueryHandler<ListPermissionsQuery, ResponseEntity<List<PermissionSummaryDTO>>> {

    private final PermissionNames names;

    public ListPermissionsQueryHandler(PermissionNames names) {
        this.names = names;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<List<PermissionSummaryDTO>> handle(ListPermissionsQuery query) {
        return ResponseEntity.ok(names.list(query.applicationCode(), query.service()));
    }
}
